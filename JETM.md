## JETM

JETM (*Java Execution Time Measurement*) es una herramienta de análisis **dinámico**, de **caja blanca**, centrada en
aspectos **no funcionales** (rendimiento): mide cuánto tarda en ejecutarse cada parte del código y da estadísticas
(nº de ejecuciones, media, mínimo, máximo y total, en milisegundos).

### Configuración

- **Dependencias** (`pom.xml`, *scope* `test`): `fm.void.jetm:jetm:1.2.3` (núcleo) y `fm.void.jetm:jetm-optional:1.2.3`
  (integración con Spring AOP). No arrastran ninguna otra dependencia.
- **Tests aparte**: los tests de rendimiento llevan `@Tag("performance")`. Surefire los **excluye** del build normal
  (`<excludedGroups>performance</excludedGroups>`) y el perfil `jetm` ejecuta **solo** esos.

Los tests están en `src/test/java/org/mybatis/jpetstore/performance/`:

| Clase | Qué hace |
|---|---|
| `JetmSupport` | Código común: calentamiento, medición, lectura de resultados e informe |
| `JetmProxyConfig` | Engancha JETM a controladores, servicios y mappers con Spring AOP |
| `ServicePerformanceTest` | Mide los servicios reales sobre la base de datos HSQLDB en memoria |
| `ControllerPerformanceTest` | Mide las acciones web: controlador, servicio y consultas |
| `CartPerformanceTest` | Mide las operaciones del carrito y cómo escalan con su tamaño |

### Ejecución

```bash
./mvnw -P jetm test   # solo los tests de rendimiento
./mvnw test           # tests normales, sin los de rendimiento
```

> Hay que usar `./mvnw` y no `mvn`: el proyecto exige Maven 3.9.16 o superior.

Las tablas de resultados salen por consola y se guardan en `target/jetm/services.txt`, `target/jetm/controllers.txt`
y `target/jetm/cart.txt`.

### Cobertura

| Parte del código | Estado |
|---|---|
| `CatalogService` | ✅ 8 de 8 métodos |
| `AccountService` | ✅ 4 de 4 métodos |
| `OrderService` | ✅ 4 de 4 métodos |
| Los 7 mappers | ✅ Medidos de forma anidada dentro de cada servicio |
| `Cart` | ✅ Las 6 operaciones con lógica (el resto son getters de una línea) |
| `Order.initOrder` | ✅ |
| Los 5 controladores | ✅ 22 acciones web, llamadas con petición, sesión y modelo simulados |
| JSP y servidor | ❌ Habría que desplegar la aplicación: no se puede medir desde los tests |

### Cómo se usa JETM

Se usan las dos formas que ofrece la herramienta:

1. **Programática** (`JetmSupport.measure`): se abre un punto de medición antes de la operación y se cierra después.

   ```java
   EtmPoint point = monitor.createPoint("Cart.getSubTotal(1000 lines)");
   try {
     cart.getSubTotal();
   } finally {
     point.collect();
   }
   ```

2. **Declarativa con Spring AOP** (`ServicePerformanceTest.Context`): el interceptor `EtmMethodCallInterceptor` de
   JETM se aplica a todos los servicios y mappers con un `BeanNameAutoProxyCreator`, **sin tocar el código de la
   aplicación**. Con un `NestedMonitor` las mediciones quedan anidadas: escenario → servicio → consultas a la base de
   datos. Así se ve **por dentro** en qué se va el tiempo de cada operación (caja blanca).

Otros elementos de JETM usados:

| Elemento | Uso |
|---|---|
| `NestedMonitor` / `FlatMonitor` | Monitor con mediciones anidadas (servicios) o planas (carrito) |
| `Java15NanoTimer` | Temporizador con `System.nanoTime()`, con precisión de nanosegundos |
| `RootAggregator` | Agrega las mediciones al momento (sin búfer), para poder comprobarlas en el test |
| `SimpleTextRenderer` | Dibuja la tabla de resultados |

### Metodología

Medir tiempos en Java tiene trampas; así se han tratado:

- **Calentamiento**: el JIT optimiza el código mientras se ejecuta, así que las primeras ejecuciones son más lentas.
  Antes de medir se ejecuta cada operación 200 veces sin medir y, en los servicios, además 1.000 rondas con todas
  las operaciones (para calentar también el código común de MyBatis, JDBC y HSQLDB). Durante el calentamiento la
  recogida de JETM está desactivada (`disableCollection`).
- **Repetición**: cada operación se mide 1.000 veces y se analizan media, mínimo y máximo.
- **Código muerto**: en el carrito el resultado se guarda en un campo `volatile` para que el JIT no elimine el cálculo.
- **Estado estable**: las operaciones que modifican el carrito se deshacen entre medidas, fuera de la medición.
- **Límites**: cada test comprueba que el tiempo medio está por debajo de un límite de **más de 10 veces lo medido**
  (5 ms en lecturas, 10 ms en escrituras, 5 ms en el subtotal de 10.000 líneas y 1 ms en el resto de operaciones del
  carrito). Así el test no falla por la variación normal entre ordenadores, pero sí si alguien empeora el rendimiento
  de forma seria.
- **Nº de consultas**: además del tiempo, los tests comprueban cuántas consultas hace cada operación (por ejemplo,
  `getOrder` con 3 líneas debe hacer exactamente 3 `ItemMapper.getItem`).

### Resultados

Ejemplo de una ejecución (Windows 11, JDK 21; los tiempos varían según el ordenador).

**Servicios**, ordenados de más rápido a más lento (ms):

| Escenario | Media | Mín. | Máx. | Consultas |
|---|---|---|---|---|
| `domain.initOrder(3 lines)` | 0.003 | 0.001 | 0.047 | 0 |
| `catalog.getCategory(FISH)` | 0.016 | 0.006 | 0.145 | 1 |
| `catalog.getProduct(FI-SW-01)` | 0.024 | 0.010 | 1.744 | 1 |
| `catalog.getProductListByCategory(FISH)` | 0.027 | 0.009 | 0.558 | 1 |
| `catalog.isItemInStock(EST-1)` | 0.030 | 0.006 | 5.824 | 1 |
| `catalog.getCategoryList()` | 0.031 | 0.009 | 3.727 | 1 |
| `catalog.searchProductList(fish)` | 0.037 | 0.016 | 1.380 | 1 |
| `account.getAccount(j2ee)` | 0.046 | 0.015 | 3.242 | 1 |
| `account.getAccount(j2ee, j2ee)` (login) | 0.060 | 0.015 | 4.017 | 1 |
| `catalog.searchProductList(fish dog cat)` | 0.072 | 0.018 | 5.331 | 3 |
| `catalog.getItem(EST-1)` | 0.075 | 0.020 | 2.366 | 1 |
| `catalog.getItemListByProduct(FI-SW-01)` | 0.093 | 0.025 | 4.756 | 1 |
| `order.getNextId(ordernum)` | 0.178 | 0.034 | 13.005 | 2 |
| `order.getOrdersByUsername(10 orders)` | 0.179 | 0.052 | 10.721 | 1 |
| `account.insertAccount()` | 0.291 | 0.073 | 4.615 | 3 |
| `account.updateAccount()` | 0.424 | 0.068 | 28.707 | 3 |
| `order.getOrder(3 lines)` | 0.493 | 0.180 | 5.037 | 8 |
| `order.insertOrder(3 lines)` | 0.663 | 0.241 | 6.827 | 10 |

**Acciones web** (controlador → servicio → consultas), una selección (ms):

| Acción | Media | Mín. | Máx. |
|---|---|---|---|
| `GET /catalog` (portada) | 0.001 | 0.001 | 0.028 |
| `GET /cart` (ver carrito) | 0.004 | 0.002 | 0.997 |
| `POST /order/new` (confirmed=false) | 0.006 | 0.003 | 0.102 |
| `GET /order/new` (formulario de pedido) | 0.009 | 0.005 | 0.183 |
| `POST /cart/update` | 0.012 | 0.005 | 0.995 |
| `GET /catalog/search?keyword=fish` | 0.047 | 0.021 | 2.699 |
| `GET /catalog/category?categoryId=FISH` | 0.070 | 0.037 | 5.646 |
| `GET /catalog/product?productId=FI-SW-01` | 0.136 | 0.065 | 1.324 |
| `POST /account/signon` (login) | 0.186 | 0.074 | 4.753 |
| `GET /order/list` (10 pedidos) | 0.249 | 0.115 | 2.622 |
| `POST /account/new` (registro) | 0.773 | 0.330 | 6.913 |
| **`GET /order/view?orderId`** | **1.127** | 0.438 | 13.842 |
| `POST /account/edit` | 1.144 | 0.485 | 6.533 |
| **`POST /order/new` (confirmed=true)** | **1.217** | 0.612 | 7.655 |

**Carrito** (ms):

| Operación | Media |
|---|---|
| `containsItemId` | < 0.001 |
| `incrementQuantityByItemId` | < 0.001 |
| `setQuantityByItemId` | < 0.001 |
| `addItem` (producto nuevo) | 0.001 |
| `removeItemById` | 0.007 |
| `getSubTotal` (10 / 100 / 1.000 / 10.000 líneas) | 0.005 / 0.021 / 0.048 / 0.289 |

### Análisis

- **La capa web casi no cuesta nada.** Comparando cada acción con su servicio, el controlador añade entre 0,002 y
  0,01 ms: lo que hace es leer la sesión, llamar al servicio y rellenar el modelo. Las acciones que no tocan la base
  de datos (ver el carrito, la portada, el paso intermedio del pedido) se quedan en 0,001–0,009 ms. **El coste de una
  petición está en el servicio y en sus consultas, no en el controlador.**
- **Las dos acciones más lentas de la aplicación son `GET /order/view` (1,13 ms) y la confirmación del pedido
  (1,22 ms)**, y las dos lo son por el servicio que llaman, no por el controlador.

- **Todas las operaciones cumplen los límites** con mucho margen. El tiempo lo marca el **número de sentencias SQL**:
  las operaciones de una sola consulta tardan entre 0,016 y 0,09 ms, y las que ejecutan varias suben a 0,2–0,7 ms.
- **`getOrder` hace 2 + 2·n consultas** (8 para un pedido de 3 líneas): lee el pedido, sus líneas y, **por cada
  línea**, el artículo y su stock. Es el problema **N+1**, y por eso es la lectura más lenta. Con pedidos grandes el
  tiempo crecerá con el nº de líneas. Mejora posible: una sola consulta con `JOIN`.
- **`searchProductList` hace una consulta por palabra**: con 3 palabras tarda el doble que con 1
  (`ProductMapper.searchProductList` aparece 3.000 veces en 1.000 búsquedas). Mejora posible: una sola consulta con `OR`.
- **`insertOrder` ejecuta 10 sentencias** en una transacción: leer y actualizar la secuencia, 3 actualizaciones de
  stock, el pedido, su estado y 3 líneas. La suma de las sentencias es ~0,53 ms de los ~0,65 ms del servicio; el resto
  corresponde a abrir y confirmar la transacción. La primera consulta de cada transacción (`SequenceMapper.getSequence`
  en `insertOrder`, `OrderMapper.getOrder` en `getOrder`) es más lenta que consultas parecidas, probablemente porque
  incluye abrir la sesión de MyBatis. `getNextId` no aparece como medición propia dentro de `insertOrder` porque se
  llama desde el propio servicio (`this.getNextId`) y esa llamada no pasa por el proxy de Spring.
- **La lógica en memoria es despreciable**: preparar un pedido (`initOrder`) tarda 0,003 ms, unas 200 veces menos que
  guardarlo. El coste está en la base de datos, no en el dominio.
- **En el carrito, quitar un producto es la operación más cara** (0,007 ms), unas 10 veces más que las demás: las otras
  usan el `Map` interno (inmediatas), mientras que `removeItemById` recorre además la `List` para localizar la línea.
  Con un carrito realista da igual, pero es lo que peor escala.
- **El subtotal escala de forma lineal**: de 1.000 a 10.000 líneas el tiempo crece ~6 veces (menos de 10 porque con
  pocos elementos pesa más el coste fijo de crear el *stream* y de la propia medición). Con 10 líneas (~5 µs) el
  tiempo está cerca de lo que cuesta medirlo, así que ese valor es poco fiable.
- **El máximo es mucho mayor que la media** (por ejemplo, 28,7 ms frente a 0,42 ms en `updateAccount`): son pausas del
  recolector de basura, compilaciones del JIT o del sistema operativo. Por eso se repite 1.000 veces y se mira la
  media, que aun así se ve algo afectada por esos picos (JETM no da la mediana).
- **Efecto del calentamiento**: en una primera versión sin calentamiento global, el primer test en ejecutarse
  (`searchProductList(fish)`) daba 0,092 ms de media; con calentamiento, 0,029 ms (3 veces menos).

### Limitaciones

- JETM lleva más de 10 años sin actualizarse (versión 1.2.3), aunque funciona con Java 21.
- No es una herramienta de *microbenchmarking* como JMH: no controla el JIT, el recolector de basura ni ejecuta cada
  medición en una JVM separada. Los tiempos son orientativos.
- La base de datos es HSQLDB **en memoria**: en producción, con una base de datos real (red y disco), los tiempos
  serían mayores.
- Se mide con un solo hilo: no se prueba el rendimiento con varios usuarios a la vez (carga o concurrencia).
- Las operaciones más rápidas del carrito quedan por debajo de 1 µs, que es el límite de resolución de la tabla.
