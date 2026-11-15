# Informe de pruebas de mutación con PITest

## 1. Introducción

[PITest (PIT)](https://pitest.org/) es una herramienta de **pruebas de mutación** para Java. Sirve para
evaluar la **calidad de las pruebas**, no del código: introduce automáticamente pequeños fallos
(*mutantes*) en el bytecode de las clases de producción, por ejemplo cambiar un `==` por `!=`, eliminar una
llamada a un método o devolver `null` en lugar del valor real, y ejecuta los tests sobre cada mutante.

- Si algún test falla, el mutante queda **eliminado** (*killed*): los tests detectan ese fallo.
- Si todos los tests pasan, el mutante **sobrevive** (*survived*): los tests ejecutan ese código pero no
  comprueban su resultado.

A diferencia de la cobertura (JaCoCo), que solo indica qué líneas **se ejecutan**, PITest indica qué
líneas **están realmente verificadas** por las aserciones de los tests.

| Clasificación | |
| --- | --- |
| Tipo de análisis | Dinámico (ejecuta los tests) |
| Caja | Blanca (modifica el código interno) |
| Objetivo | Evaluar la calidad de las pruebas |

| Elemento | Valor |
| --- | --- |
| Plugin Maven | `org.pitest:pitest-maven` **1.25.8** |
| Soporte JUnit 5 | `org.pitest:pitest-junit5-plugin` **1.2.3** |
| Código mutado | `src/main/java` (paquetes `org.mybatis.jpetstore.*`) |
| Tests usados | Tests unitarios de `src/test/java` (patrón `org.mybatis.jpetstore.*Test`) |

---

## 2. Cambios realizados en el proyecto para implementarlo

### Archivos modificados / creados

| Archivo | Carpeta | Tipo de cambio | Descripción |
| --- | --- | --- | --- |
| `pom.xml` | raíz del proyecto | Modificado | Se añade el plugin `pitest-maven` dentro de `<build><plugins>` y se excluyen los tests de rendimiento de JETM. |
| `piTest.md` | `informes/` | Creado | Resumen de la herramienta. |
| `piTest-detallado.md` | `informes/` | Creado | Este informe. |

**No se ha modificado ningún fichero de código fuente ni de tests** (`src/main/java`, `src/test/java`,
`src/jqwik-test/java`): la herramienta se ha integrado únicamente a nivel de configuración de build.

### Fragmento añadido al `pom.xml`

```xml
<plugin>
  <groupId>org.pitest</groupId>
  <artifactId>pitest-maven</artifactId>
  <version>1.25.8</version>
  <configuration>
    <targetTests>
      <param>org.mybatis.jpetstore.*Test</param>
    </targetTests>
    <excludedTestClasses>
      <param>org.mybatis.jpetstore.ScreenTransitionIT</param>
      <param>org.mybatis.jpetstore.performance.*</param>
    </excludedTestClasses>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>org.pitest</groupId>
      <artifactId>pitest-junit5-plugin</artifactId>
      <version>1.2.3</version>
    </dependency>
  </dependencies>
</plugin>
```

- **`targetTests`**: solo se usan como tests las clases cuyo nombre termina en `Test`, es decir, los tests
  unitarios (dominio, *mappers*, servicios y controladores).
- **`excludedTestClasses`**: se excluye `ScreenTransitionIT`, la prueba de integración que levanta la
  aplicación y la recorre con un navegador (Selenide). Es lenta, depende de un servidor y un navegador, y
  ejecutarla una vez por cada mutante sería inviable.
- **`excludedTestClasses` (`performance.*`)**: se excluyen también los tests de rendimiento de JETM
  (`CartPerformanceTest`, `ControllerPerformanceTest`, `ServicePerformanceTest`). Miden tiempos, no
  comprueban resultados funcionales, así que no pueden eliminar mutantes y solo alargarían la ejecución.
  Los tests de ArchUnit (`ArchitectureTest`) sí se ejecutan, aunque al comprobar la estructura y no el
  comportamiento tampoco eliminan mutantes.
- **`pitest-junit5-plugin`**: los tests de JPetStore usan JUnit 5 (Jupiter). Sin este plugin PITest no
  encuentra ningún test. También permite ejecutar los tests de jqwik, que funcionan sobre la plataforma
  JUnit 5.
- No se ha cambiado el conjunto de mutadores, así que se usan los **mutadores por defecto** (`DEFAULTS`).
- El plugin no tiene `<executions>`, así que **no se ejecuta en `mvn package`/`mvn verify`**: hay que
  invocarlo explícitamente (ver sección 4).

---

## 3. Mutadores aplicados

Son los mutadores del grupo `DEFAULTS` de PITest; cada uno genera un tipo de mutante:

| Mutador | Qué cambia en el código | Ejemplo |
| --- | --- | --- |
| `NegateConditionals` | Invierte las condiciones. | `if (a != null)` → `if (a == null)` |
| `ConditionalsBoundary` | Cambia los límites de las comparaciones. | `x < 1` → `x <= 1` |
| `Math` | Cambia operadores aritméticos. | `a + b` → `a - b` |
| `VoidMethodCall` | Elimina llamadas a métodos `void`. | `session.setAttribute(...)` → *(nada)* |
| `EmptyObjectReturnVals` | Devuelve un objeto "vacío". | `return name;` → `return "";` |
| `NullReturnVals` | Devuelve `null`. | `return list;` → `return null;` |
| `PrimitiveReturns` | Devuelve `0` en métodos numéricos. | `return qty;` → `return 0;` |
| `BooleanTrueReturnVals` | Devuelve siempre `true`. | `return ok;` → `return true;` |
| `BooleanFalseReturnVals` | Devuelve siempre `false`. | `return ok;` → `return false;` |

Estados posibles de un mutante:

| Estado | Significado |
| --- | --- |
| `KILLED` | Al menos un test falla con el mutante: **los tests detectan el fallo**. |
| `SURVIVED` | Todos los tests pasan: el código se ejecuta pero **no se comprueba su resultado**. |
| `NO_COVERAGE` | Ningún test ejecuta esa línea: **no hay pruebas** para ese código. |
| `TIMED_OUT` / `MEMORY_ERROR` / `RUN_ERROR` | El mutante provoca un bucle infinito o un error; se cuentan como detectados. |

---

## 4. Cómo ejecutarlo

Desde la raíz del proyecto (en Windows se usa `.\mvnw.cmd` en lugar de `./mvnw`). PITest necesita las
clases compiladas, por eso se ejecuta junto a `test-compile`:

```bash
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
```

Opciones útiles:

```bash
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage -DoutputFormats=HTML,XML,CSV -DtimestampedReports=false
```

- `-DoutputFormats=HTML,XML,CSV`: además del informe HTML, genera `mutations.xml` y `mutations.csv` con
  todos los mutantes (útil para guardar y comparar ejecuciones).
- `-DtimestampedReports=false`: escribe el informe directamente en `target/pit-reports/` en lugar de en
  una subcarpeta con fecha y hora.
- `-Dverbose=true`: muestra información detallada de la ejecución.

### Ejecución con todos los tests (incluidos los de jqwik)

Los tests de jqwik están en `src/jqwik-test/java` y solo se compilan con el perfil `jqwik`. Para
incluirlos en el análisis de mutación:

```bash
./mvnw -P jqwik test-compile org.pitest:pitest-maven:mutationCoverage -DoutputFormats=HTML,XML,CSV -DtimestampedReports=false
```

En Windows (PowerShell):

```powershell
.\mvnw.cmd -P jqwik test-compile org.pitest:pitest-maven:mutationCoverage "-DoutputFormats=HTML,XML,CSV" "-DtimestampedReports=false"
```

- `-P jqwik`: añade `src/jqwik-test/java` y la dependencia de jqwik.
- **`<skipFailingTests>true</skipFailingTests>`** (en el `pom.xml`): PITest exige que todos los tests
  pasen antes de mutar el código (*green suite*). Tres propiedades de jqwik fallan porque han encontrado
  errores reales en el programa, así que con esta opción PITest las ignora y usa el resto. Esta opción
  **solo se puede configurar en el `pom.xml`**: el plugin no la lee como `-DskipFailingTests=true`.

  | Test que falla | Error que detecta | Issue |
  | --- | --- | --- |
  | `CartPropertiesTest.changingAnItemPriceKeepsLineTotalAndSubtotalConsistent` | Total de línea y subtotal inconsistentes al cambiar el precio | #10 |
  | `CartPropertiesTest.settingQuantityNeverLeavesANegativeCartQuantity` | El carrito admite cantidades negativas | #11 |
  | `ControllerPropertiesTest.cartUpdateQuantitiesHandlesValidInvalidAndZero` | Al poner la cantidad a `0`, el producto se borra de la lista del carrito pero no de su mapa interno (ver sección 6.4) | #12 |

> Nota: si el build falla en el plugin `rewrite-maven-plugin` (OpenRewrite) por no poder descargar
> dependencias, se puede saltar ese paso añadiendo `-Drewrite.skip=true`.

### Informe generado

```text
target/pit-reports/index.html      -> informe HTML navegable (por paquete, clase y línea)
target/pit-reports/mutations.xml   -> todos los mutantes en XML
target/pit-reports/mutations.csv   -> todos los mutantes en CSV
```

En el HTML, cada línea de código aparece coloreada: **verde** si todos sus mutantes fueron eliminados,
**rojo** si sobrevivió alguno o no tiene cobertura.

---

## 5. Resultados del análisis (punto de partida: tests originales de JPetStore)

Ejecución del 26/10/2026, con las **18 clases de test originales** del proyecto, sin ningún test añadido.
Este resultado sirve como **línea base** para compararlo con el de después de añadir nuevas pruebas
(ver sección 6).

### 5.1 Resumen general

| Métrica | Valor |
| --- | --- |
| Clases de test examinadas | 18 |
| Mutantes generados | 273 |
| Mutantes eliminados (`KILLED`) | 224 |
| Mutantes supervivientes (`SURVIVED`) | 31 |
| Mutantes sin cobertura (`NO_COVERAGE`) | 18 |
| **Mutation score** (eliminados / generados) | **82 %** |
| **Test strength** (eliminados / cubiertos) | **88 %** |
| Cobertura de líneas (clases mutadas) | 573 / 602 (95 %) |
| Tests ejecutados | 346 (1,27 tests por mutante) |
| Tiempo de ejecución | ≈ 1 min 31 s |

La diferencia entre la **cobertura de líneas (95 %)** y el **mutation score (82 %)** es precisamente lo
que aporta PITest: hay código que los tests ejecutan pero cuyo resultado no comprueban.

### 5.2 Resultados por mutador

| Mutador | Generados | Eliminados | Sobreviven | Sin cobertura | % eliminados |
| --- | ---: | ---: | ---: | ---: | ---: |
| `EmptyObjectReturnVals` | 102 | 91 | 5 | 6 | 89 % |
| `VoidMethodCall` | 70 | 40 | 18 | 12 | **57 %** |
| `NegateConditionals` | 48 | 41 | 7 | 0 | 85 % |
| `NullReturnVals` | 25 | 25 | 0 | 0 | 100 % |
| `PrimitiveReturns` | 10 | 10 | 0 | 0 | 100 % |
| `BooleanFalseReturnVals` | 7 | 7 | 0 | 0 | 100 % |
| `BooleanTrueReturnVals` | 6 | 6 | 0 | 0 | 100 % |
| `Math` | 3 | 3 | 0 | 0 | 100 % |
| `ConditionalsBoundary` | 2 | 1 | 1 | 0 | 50 % |
| **Total** | **273** | **224** | **31** | **18** | **82 %** |

El punto más débil es `VoidMethodCall`: los tests no comprueban los **efectos secundarios** de muchos
métodos `void` (por ejemplo, que se guarde algo en la sesión HTTP o que se modifique un objeto).

### 5.3 Resultados por paquete

| Paquete | Generados | Eliminados | Sobreviven | Sin cobertura | % eliminados |
| --- | ---: | ---: | ---: | ---: | ---: |
| `domain` | 114 | 103 | 9 | 2 | 90 % |
| `service` | 40 | 40 | 0 | 0 | **100 %** |
| `web.controllers` | 119 | 81 | 22 | 16 | **68 %** |
| `mapper` | – | – | – | – | – |

Los *mappers* de MyBatis son interfaces sin código Java propio (las consultas están en XML), por lo que
PITest no genera mutantes sobre ellos.

### 5.4 Resultados por clase

| Clase | Generados | Eliminados | Sobreviven | Sin cobertura | % eliminados |
| --- | ---: | ---: | ---: | ---: | ---: |
| `web.controllers.OrderController` | 61 | 30 | 16 | 15 | **49 %** |
| `web.controllers.AccountController` (+ `AccountSession`) | 23 | 17 | 5 | 1 | 74 % |
| `domain.Order` | 32 | 28 | 4 | 0 | 88 % |
| `domain.LineItem` | 11 | 9 | 2 | 0 | 82 % |
| `domain.Product` | 5 | 4 | 1 | 0 | 80 % |
| `domain.Category` | 4 | 3 | 0 | 1 | 75 % |
| `domain.CartItem` | 10 | 9 | 1 | 0 | 90 % |
| `domain.Item` | 13 | 12 | 0 | 1 | 92 % |
| `domain.Cart` | 17 | 16 | 1 | 0 | 94 % |
| `web.controllers.CartController` | 24 | 23 | 1 | 0 | 96 % |
| `domain.Account` | 20 | 20 | 0 | 0 | 100 % |
| `domain.Sequence` | 2 | 2 | 0 | 0 | 100 % |
| `service.OrderService` | 18 | 18 | 0 | 0 | 100 % |
| `service.AccountService` | 11 | 11 | 0 | 0 | 100 % |
| `service.CatalogService` | 11 | 11 | 0 | 0 | 100 % |
| `web.controllers.CatalogController` | 10 | 10 | 0 | 0 | 100 % |
| `web.controllers.RootController` | 1 | 1 | 0 | 0 | 100 % |

### 5.5 Análisis de los mutantes no eliminados

**`OrderController` (31 de los 49 mutantes no eliminados, casi todos en `newOrder`).** Es con diferencia el punto más
débil de las pruebas:

- **15 sin cobertura**: ningún test llega al bloque que copia los datos del formulario al pedido de la
  sesión (líneas 164-184) ni a la rama de dirección de envío (líneas 199-201), ni a `newOrderForm`
  (línea 118).
- **16 supervivientes**: los tests ejecutan el método, pero no comprueban el contenido del pedido
  resultante. Por ejemplo, PITest invierte las condiciones de los `if` sin llaves (líneas 167-183):

  ```java
  if (order.getCreditCard() != null)          // mutado a: == null
    sessionOrder.setCreditCard(order.getCreditCard());
  ```

  o elimina las llamadas `setShipTo...` (líneas 186-193) y `session.setAttribute("cart", new Cart())`
  (línea 149), y ningún test falla.

**`AccountController` (5 supervivientes).** Se eliminan llamadas `void` sin que ningún test lo detecte:

- `signon` (líneas 96 y 98): `account.setPassword(null)` y `session.setAttribute("accountBean", ...)`.
  Nadie comprueba que la contraseña se borre del objeto en sesión, lo que además es un aspecto de
  seguridad.
- `newAccount` (línea 142) y `editAccount` (líneas 179 y 182): actualización de la sesión tras crear o
  editar la cuenta.

**Clases de dominio (9 supervivientes).**

- `Order.getBillToFirstName/LastName`, `getShipToFirstName/LastName`: devolver `""` no hace fallar
  ningún test.
- `Cart.addItem` (línea 101), `CartItem.setItem`, `LineItem.setItem` y `LineItem.setQuantity`: se elimina
  la llamada a un *setter* y ningún test lo nota.
- `Product.toString`, `Category.toString`, `Item.toString`: no se prueban.

**`CartController.updateCartQuantities` (línea 164).** El mutante de límite `quantity < 1` →
`quantity <= 1` sobrevive: no hay ningún test con cantidad exactamente `1`, que es el **valor límite**.
Es un caso claro para una prueba de valores límite o para generar cantidades aleatorias con jqwik.

### 5.6 Conclusiones

- Los tests originales tienen una calidad **alta** (82 % de mutation score, 88 % de *test strength*),
  pero la cobertura del 95 % sobreestima su eficacia real.
- La capa de **servicio** está perfectamente probada (100 %).
- La capa **web**, y en concreto `OrderController.newOrder`, concentra la mayoría de los problemas: código
  sin probar y comprobaciones insuficientes sobre la sesión y el pedido.
- Mejoras propuestas para los tests:
  - Probar `newOrder` con formularios de facturación y de envío, comprobando campo a campo el pedido
    guardado en sesión.
  - Verificar con Mockito (`verify(session).setAttribute(...)`) o con aserciones sobre la sesión los
    efectos de `signon`, `newAccount`, `editAccount` y `newOrder`.
  - Añadir pruebas de valores límite (cantidad `0`, `1`, `2`) en `updateCartQuantities`, por ejemplo con
    propiedades de jqwik.
  - Comprobar los *getters*/*setters* de `Order`, `LineItem` y `CartItem` que hoy no se verifican.

---

## 6. Comparación antes / después de añadir nuevas pruebas

Tras integrar en la rama los tests desarrollados con las demás herramientas, se vuelve a ejecutar PITest
con el comando de la sección 4 *Ejecución con todos los tests*. El código de producción
(`src/main/java`) **no ha cambiado** entre las dos ejecuciones: solo se han añadido tests. Por tanto, la
diferencia en el *mutation score* se debe únicamente a las pruebas nuevas.

Ejecución del 15/11/2026 con 23 clases de test: las 18 originales, las 4 de jqwik y la de ArchUnit.

### 6.1 Tests añadidos

| Clase de test | Herramienta | Clases probadas | Casos | Qué comprueba |
| --- | --- | --- | ---: | --- |
| `domain/CartPropertiesTest` | jqwik | `Cart`, `CartItem` | 9 | Cantidades y subtotales del carrito con datos aleatorios (2 propiedades fallan porque han encontrado errores reales) |
| `domain/DomainPropertiesTest` | jqwik | `Product`, `Category`, `Item`, `LineItem`, `Account`, `Order`, `Sequence` | 11 | *Getters*/*setters*, constructores, total de línea (precio × cantidad), copia de datos de cuenta y carrito en `Order` |
| `service/ServicePropertiesTest` | jqwik | `AccountService`, `CatalogService`, `OrderService` | 13 | Delegación en los *mappers*, actualización condicional de la contraseña, stock, secuencia de ids |
| `web/controllers/ControllerPropertiesTest` | jqwik | Todos los controladores | 24 | Vistas devueltas, redirecciones, autenticación, sesión, cantidades del carrito y flujo de `newOrder` (1 propiedad falla porque ha encontrado un error real) |
| `ArchitectureTest` | ArchUnit | Todo el proyecto | 19 | Reglas de arquitectura; no comprueba comportamiento, así que no elimina mutantes |
| `performance/*PerformanceTest` | JETM | Servicios, controladores y carrito | – | Excluidos de PITest (miden tiempos, no resultados) |

### 6.2 Resumen comparativo

| Métrica | Antes (tests originales) | Después (con tests nuevos) | Diferencia |
| --- | ---: | ---: | ---: |
| Clases de test examinadas | 18 | 23 | +5 |
| Tests ejecutados | 346 | 414 | +68 |
| Mutantes generados | 273 | 273 | 0 |
| Mutantes eliminados | 224 | 242 | **+18** |
| Mutantes supervivientes | 31 | 31 | 0 |
| Mutantes sin cobertura | 18 | 0 | **−18** |
| **Mutation score** | **82 %** | **89 %** | **+7 pp** |
| **Test strength** | **88 %** | **89 %** | +1 pp |
| Cobertura de líneas | 95 % (573/602) | **100 %** (602/602) | +5 pp |
| Tiempo de ejecución | ≈ 1 min 31 s | ≈ 3 min 56 s | +2 min 25 s |

Se generan los mismos 273 mutantes porque el código de producción no ha cambiado.

**Por mutador:**

| Mutador | % eliminados antes | % eliminados después |
| --- | ---: | ---: |
| `EmptyObjectReturnVals` | 89 % | **99 %** |
| `VoidMethodCall` | 57 % | **69 %** |
| `NegateConditionals` | 85 % | 85 % |
| `ConditionalsBoundary` | 50 % | 50 % |
| Resto (`NullReturnVals`, `PrimitiveReturns`, `Boolean*`, `Math`) | 100 % | 100 % |

### 6.3 Comparación por clase (clases con mutantes no eliminados)

| Clase | Score antes | Score después | Diferencia |
| --- | ---: | ---: | ---: |
| `OrderController` | 49 % | 59 % | +10 pp |
| `AccountController` (+ `AccountSession`) | 74 % | 91 % | +17 pp |
| `Category` | 75 % | 100 % | +25 pp |
| `Product` | 80 % | 100 % | +20 pp |
| `LineItem` | 82 % | 91 % | +9 pp |
| `Order` | 88 % | 100 % | +12 pp |
| `CartItem` | 90 % | 90 % | 0 |
| `Item` | 92 % | 100 % | +8 pp |
| `Cart` | 94 % | 94 % | 0 |
| `CartController` | 96 % | 96 % | 0 |

Las clases que ya tenían un 100 % (`Account`, `Sequence`, servicios, `CatalogController`, `RootController`)
lo mantienen.

### 6.4 Conclusiones de la comparación

**Los tests nuevos mejoran la calidad de las pruebas.** El *mutation score* sube del 82 % al 89 %: los
tests eliminan 18 mutantes más con el mismo código.

**Mutantes eliminados por los tests nuevos:**

- *Getters* de `Order` (`getBillToFirstName`, `getBillToLastName`, `getShipToFirstName`,
  `getShipToLastName`), comprobados por `DomainPropertiesTest.orderPropertiesRoundTrip`.
- `toString` de `Product`, `Category` e `Item`, y `LineItem.setQuantity`.
- La actualización de la sesión en `AccountController.newAccount`, `editAccount` y `signon` (línea 98).
- Parte del código de `OrderController.newOrder` que antes no se ejecutaba (líneas 118, 155, 201).

**La cobertura del 100 % no significa pruebas perfectas.** Los tests de jqwik llegan a todas las líneas
(los 18 mutantes sin cobertura desaparecen), pero el número de supervivientes sigue en 31. Gran parte del
código que antes no se ejecutaba ahora se ejecuta **sin comprobar su resultado**. El caso más claro es
`OrderController.newOrder`: `ControllerPropertiesTest.orderNewOrderRouting` recorre el bloque que copia
los campos del formulario al pedido de la sesión (líneas 164-184), pero solo comprueba la vista devuelta.
Por eso, de los 12 mutantes de ese bloque que antes no tenían cobertura (líneas 164-184 y 199), 10 pasan
a ser supervivientes y solo 2 quedan eliminados. Es justo la diferencia
entre cobertura y calidad que mide PITest.

**Mutantes que siguen vivos (31):**

| Clase / método | Mutantes | Por qué sobreviven |
| --- | ---: | --- |
| `OrderController.newOrder` | 25 | Los tests no comprueban los campos del pedido guardado en sesión ni que se vacíe el carrito tras confirmar (línea 149). |
| `AccountController.signon` (línea 96) | 1 | Ningún test comprueba que se borre la contraseña (`setPassword(null)`) antes de guardar la cuenta en sesión. |
| `AccountController.AccountSession.getMyList` (línea 230) | 1 | Antes no tenía cobertura; ahora se ejecuta, pero nadie comprueba la lista devuelta. |
| `Cart.addItem`, `CartItem.setItem`, `LineItem.setItem` | 3 | Se elimina la llamada a un *setter* y ningún test lo nota. |
| `CartController.updateCartQuantities` (línea 164) | 1 | Ver el siguiente punto. |

**Un error del programa oculta un mutante.** `ControllerPropertiesTest.cartUpdateQuantitiesHandlesValidInvalidAndZero`
genera cantidades entre 0 y 10, incluido el valor frontera `1`, así que eliminaría el mutante `< 1` → `<= 1`
de la línea 164. Pero esa propiedad falla siempre por un error real, y PITest la descarta
(`skipFailingTests`). El fallo ya está registrado en la issue **#12 «La actualización del carrito acepta una cantidad inválida»**; al analizarlo con PITest se ha
localizado su causa:

```java
// CartController.updateCartQuantities
if (quantity < 1) {
  cartItems.remove();   // borra el CartItem de Cart.itemList, pero NO de Cart.itemMap
}
```

`Cart` guarda los productos en dos estructuras, `itemList` y `itemMap`, y `cartItems` es el iterador de
`itemList`. Al poner la cantidad a `0`, el producto desaparece de la lista pero sigue en el mapa, de modo
que `containsItemId` devuelve `true`. Si después el usuario vuelve a añadir el mismo producto,
`Cart.addItem` lo encuentra en el mapa y solo incrementa su cantidad, pero **no lo vuelve a meter en la
lista**: el producto no aparece en el carrito ni cuenta en el subtotal. Cuando se corrija (por ejemplo,
usando `cart.removeItemById(itemId)`), la propiedad pasará y el mutante de la línea 164 quedará eliminado.
