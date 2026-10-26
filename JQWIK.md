# Pruebas dinámicas con jqwik

## Separación de las pruebas

Las pruebas que ya existían en el repositorio permanecen en
`src/test/java`. Todo el código de jqwik está en la carpeta independiente
`src/jqwik-test/java`, por lo que no se mezclan ambos conjuntos:

```text
src/
├── test/java/       # JUnit existente
└── jqwik-test/java/ # propiedades jqwik
```

El perfil Maven `jqwik`:

1. añade `src/jqwik-test/java` como fuente de pruebas;
2. añade la dependencia `net.jqwik:jqwik`; y
3. limita Surefire a las clases cuyo nombre termina en `PropertiesTest`.

La configuración está en el perfil `jqwik` de [pom.xml](pom.xml), no en la
carpeta de pruebas existente.

## Cómo se han diseñado las pruebas

Son pruebas dinámicas porque jqwik genera automáticamente muchos valores
distintos para cada ejecución (`@ForAll`). Son de caja negra porque solamente
usan únicamente interfaces públicas, sin acceder a detalles privados de la
implementación.

Las propiedades están organizadas por capas:

- `CartPropertiesTest`: operaciones del carrito e invariantes de cantidades,
  inventario y subtotales.
- `DomainPropertiesTest`: contratos de `Item`, `Product`, `Category` y
  cálculo de `LineItem`.
- `ServicePropertiesTest`: catálogo, cuentas y secuencias de pedidos con
  mappers simulados.
- `ControllerPropertiesTest`: redirección raíz y validación de rutas de
  catálogo y carrito mediante modelos y sesiones públicos.

En [CartPropertiesTest.java](src/jqwik-test/java/org/mybatis/jpetstore/domain/CartPropertiesTest.java)
se comprueban:

- **Pruebas funcionales:** añadir el mismo artículo acumula la cantidad y su
  subtotal; el subtotal de un carrito con varios artículos es la suma de sus
  totales.
- **Prueba no funcional de robustez/carga:** se generan cantidades de hasta
  1.000 unidades y precios de hasta 1.000,00; se comprueba que el carrito
  conserva un estado consistente y calcula el total sin perder datos.

Cada propiedad se ejecuta con múltiples combinaciones aleatorias y jqwik
informa del número de casos, los casos límite y la semilla. Si aparece un
fallo, esa semilla permite reproducirlo.

## Ejecución y evidencia

Ejecutar todas las pruebas jqwik, sin ejecutar las pruebas JUnit existentes:

```sh
./mvnw -P jqwik test
```

Para ejecutar una clase concreta o reproducir una ejecución:

```sh
./mvnw -P jqwik -Dtest=ServicePropertiesTest test
./mvnw -P jqwik -Dtest=CartPropertiesTest -Djqwik.seed=SEED test
```

La ejecución inicial tenía tres propiedades correctas. Para intentar encontrar
fallos se añadieron dos propiedades de exploración:

- cambiar el precio de un artículo después de añadirlo al carrito;
- establecer una cantidad negativa.

Al ejecutarlas, jqwik encuentra contraejemplos mínimos: `CartItem.total` no se
actualiza cuando cambia el precio de `Item`, y `Cart.setQuantityByItemId`
acepta cantidades negativas. Esto es un resultado útil de la prueba de caja
negra: demuestra fallos observables mediante la API pública, sin inspeccionar
la implementación interna.

El número de casos generados aparece además en las líneas
`tries`, `checks` y `seed` del informe de jqwik.

Ejecutar las pruebas normales del repositorio, sin activar jqwik:

```sh
./mvnw test
```

## Medir cobertura con JaCoCo

JaCoCo ya está configurado por el proyecto Maven y escribe los datos de
ejecución en `target/jacoco.exec`. Para obtener la cobertura de las pruebas
JUnit existentes, ejecutar:

```sh
./mvnw -P jqwik test jacoco:report
```

El informe HTML se genera en:

```text
target/site/jacoco/index.html
```

También se puede abrir directamente en un navegador:

```text
file:///ruta/absoluta/jpetstore-6/target/site/jacoco/index.html
```

Para medir la cobertura del código de producción ejecutado por todas las
propiedades jqwik, hay que generar el informe justo después de ejecutar jqwik.
Es
importante no ejecutar `./mvnw test` entre ambos comandos, porque volvería a
modificar `target/jacoco.exec`.

Ejecutar, desde la raíz del proyecto:

```sh
./mvnw clean -P jqwik -Dtest='*PropertiesTest' \
  -Dmaven.test.failure.ignore=true test jacoco:report
```

`-Dmaven.test.failure.ignore=true` permite que Maven continúe hasta la fase
`jacoco:report` aunque las propiedades fallen. En este caso JaCoCo genera el
informe, pero el resultado de las pruebas sigue siendo `2` fallos y debe
interpretarse como evidencia de defectos, no como una ejecución correcta.

Abrir después este archivo:

```text
target/site/jacoco/index.html
```

En la página inicial, entrar en los paquetes `domain`, `service` y
`web.controllers`. Las columnas `Missed Instructions`, `Missed Branches`,
`Missed Lines` y sus porcentajes muestran cuánto código de producción fue
cubierto por las ejecuciones jqwik. No hay que entrar en las clases
`*PropertiesTest`: JaCoCo mide la cobertura del código probado, no el
porcentaje de propiedades que pasaron.

También se puede abrir directamente:

```text
file:///ruta/absoluta/jpetstore-6/target/site/jacoco/index.html
```

El archivo `target/jacoco.exec` es la evidencia binaria de esa ejecución y
`target/site/jacoco/jacoco.csv` permite consultar los mismos datos en formato
tabular.

El porcentaje global puede parecer bajo porque JaCoCo incluye todas las clases
de la aplicación. Las propiedades cubren partes del dominio (`Cart`,
`CartItem`, `Item`, `Product`, `Category`, `LineItem`), servicios de catálogo,
cuentas y pedidos, y rutas de controladores; la cobertura exacta debe
consultarse por clase, no solo en el porcentaje global.

Después de ampliar `DomainPropertiesTest`, la cobertura jqwik observada en
`target/site/jacoco/jacoco.csv` es:

| Clase | Instrucciones cubiertas |
| --- | ---: |
| `Account` | 100 % |
| `Category` | 100 % |
| `Item` | 83 % |
| `LineItem` | 77 % |
| `Order` | 100 % |
| `Product` | 100 % |
| `Sequence` | 100 % |
| `Cart` | 95 % |
| `CartItem` | 100 % |

La ejecución final contiene 25 propiedades generadas por jqwik: 10 de dominio,
4 de servicios, 3 de controladores y 8 del carrito. Las dos propiedades
fallidas del carrito siguen siendo contraejemplos intencionados para documentar
los defectos encontrados; no se ha modificado el código de producción para
ocultarlos.

El informe muestra cobertura de líneas, instrucciones, ramas y métodos de las
clases instrumentadas. Para la entrega conviene conservar por separado:

1. la salida de jqwik, con los contraejemplos y las semillas;
2. `target/site/jacoco/index.html` o una captura del informe; y
3. la orden exacta utilizada para generar la cobertura.


# Fallos resultado de JQWIK

[ERROR] Failures: 

    [ERROR]   CartPropertiesTest.changingAnItemPriceKeepsLineTotalAndSubtotalConsistent:98 
    expected: 0.02
    but was: 0.01

    [ERROR]   CartPropertiesTest.settingQuantityNeverLeavesANegativeCartQuantity:111 
    Expecting actual:
    -1
    to be greater than or equal to:
    0