## PITest

PITest es una herramienta de **pruebas de mutación**, **dinámica** y de **caja blanca**, que evalúa la **calidad de las
pruebas**: introduce pequeños fallos (*mutantes*) en el código de producción, por ejemplo cambiar `!=` por `==` o eliminar
una llamada, y ejecuta los tests sobre cada uno.

- Si algún test falla, el mutante queda **eliminado** (*killed*): los tests detectan ese fallo.
- Si todos los tests pasan, el mutante **sobrevive** (*survived*): el código se ejecuta, pero ningún test comprueba su resultado.

### Configuración

Se añade el plugin `pitest-maven` 1.25.8 al `pom.xml`, con `pitest-junit5-plugin` 1.2.3 para que encuentre los tests
de JUnit 5 (también ejecuta los de jqwik y ArchUnit). Se usan los mutadores por defecto (`DEFAULTS`).

| Parámetro | Valor | Motivo |
|---|---|---|
| `targetTests` | `org.mybatis.jpetstore.*Test` | Solo los tests unitarios |
| `excludedTestClasses` | `ScreenTransitionIT`, `performance.*` | La prueba de integración con Selenide y los tests de rendimiento de JETM son lentos y no comprueban resultados |
| `skipFailingTests` | `true` | Tres propiedades de jqwik fallan por errores reales del programa (#10, #11, #12) y PITest exige que todos los tests pasen antes de mutar |

### Ejecución

```bash
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
```

Para incluir también los tests de jqwik (están en `src/jqwik-test/java` y solo se compilan con el perfil `jqwik`):

```bash
./mvnw -P jqwik test-compile org.pitest:pitest-maven:mutationCoverage
```

En Windows (PowerShell):

```powershell
.\mvnw.cmd -P jqwik test-compile org.pitest:pitest-maven:mutationCoverage
```

El informe HTML se genera en `target/pit-reports/`.

### Resultados

Se ejecutó PITest con los tests originales de JPetStore y después con los tests nuevos de jqwik y ArchUnit:

| Métrica | Antes | Después |
|---|---:|---:|
| Mutantes generados | 273 | 273 |
| Eliminados | 224 | 242 |
| Supervivientes | 31 | 31 |
| Sin cobertura | 18 | 0 |
| **Mutation score** | **82 %** | **89 %** |
| Test strength | 88 % | 89 % |
| Cobertura de líneas | 95 % | 100 % |

Los tests nuevos cubren todas las líneas, pero **cobertura del 100 % no significa tests perfectos**: siguen sobreviviendo
31 mutantes en código que se ejecuta pero cuyo resultado nadie comprueba.

**Carencias de las pruebas (mutantes que sobreviven)**

- **`OrderController.newOrder` (25)**: no se comprueba el pedido que se guarda en sesión ni que el carrito se vacíe al confirmar.
- **`AccountController.signon` (1)**: no se comprueba que se borre la contraseña antes de guardar la cuenta en sesión.
- **`AccountSession.getMyList` (1)**: no se comprueba la lista de favoritos que se guarda al iniciar sesión.
- **`Cart.addItem`, `CartItem.setItem`, `LineItem.setItem` (3)**: no se verifica el efecto de estas llamadas.
- **`CartController.updateCartQuantities` (1)**: lo eliminaría una propiedad de jqwik, pero esa propiedad falla por el
  error de la issue **#12** y PITest la descarta.

**Error encontrado**

Al analizar el mutante de `updateCartQuantities` se localizó la causa de la issue **#12**: al poner la cantidad de un
producto a `0`, se borra de la lista del carrito (`itemList`) pero no de su mapa (`itemMap`). Si después se vuelve a
añadir, solo se incrementa su cantidad y el producto no aparece en el carrito ni cuenta en el subtotal.

El análisis completo está en [`piTest-detallado.md`](piTest-detallado.md).
