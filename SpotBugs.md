## SpotBugs

SpotBugs es una herramienta de análisis **estático**, de **caja blanca**, centrada en aspectos **funcionales**:
analiza el bytecode (`.class`) sin ejecutarlo y busca patrones de código que suelen ser errores.

### Configuración

El plugin ya venía declarado en el POM padre (`mybatis-parent`), pero configurado para que casi no encontrase nada:

| Parámetro | Valor en `mybatis-parent` | Valor en este proyecto | Motivo |
|---|---|---|---|
| `visitors` | `FindDeadLocalStores,UnreadFields` | *(todos)* | El padre solo ejecutaba 2 detectores de los más de 400 que tiene SpotBugs |
| `threshold` | `High` | `Low` | Mostrar también los avisos de confianza media y baja |
| `effort` | `Max` | `Max` | Análisis más profundo |
| `failOnError` | `true` | `false` | Que los avisos no rompan el build |
| `excludeFilterFile` | — | `config/spotbugs/spotbugs-exclude.xml` | Falsos positivos revisados |

### Ejecución

```bash
./mvnw compile spotbugs:check     # muestra los avisos por consola
./mvnw compile spotbugs:spotbugs  # genera target/spotbugs-reports/spotbugsXml.xml
./mvnw compile spotbugs:gui       # abre la interfaz gráfica para navegar por los avisos
```

> Hay que usar `./mvnw` y no `mvn`: el proyecto exige Maven 3.9.16 o superior.

### Resultados

Sin filtro: **20 avisos**. Con el filtro de falsos positivos: **16 avisos**.

| Categoría | Patrón | Nº | Dónde |
|---|---|---|---|
| MALICIOUS_CODE | `EI_EXPOSE_REP` | 7 | Getters de `Cart`, `CartItem`, `Item`, `LineItem`, `Order`, `AccountSession` |
| MALICIOUS_CODE | `EI_EXPOSE_REP2` | 9 (6 tras el filtro) | Setters de `domain`, constructor de `AccountSession`, constructores de los servicios |
| I18N | `DM_CONVERT_CASE` | 2 | `CatalogService.searchProductList`, `CatalogController.searchProducts` |
| STYLE | `UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR` | 1 | `Order.getFormattedOrderDate` |
| STYLE | `DLS_DEAD_LOCAL_STORE` | 1 (0 tras el filtro) | `CartController.updateCartQuantities` |

### Análisis

**Errores reales**

- **`DM_CONVERT_CASE`**: `keyword.toLowerCase()` sin `Locale`. El resultado depende del idioma del servidor:
  con locale turco, `"FISH".toLowerCase()` devuelve `"fısh"` (i sin punto) y la búsqueda de productos no encuentra nada.
  Solución: `toLowerCase(Locale.ROOT)`.
- **`UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR`**: `orderDate` solo se asigna en `initOrder()` o `setOrderDate()`.
  Si se llama a `getFormattedOrderDate()` sobre un `Order` recién creado, lanza `NullPointerException`.

**Discutibles (se mantienen en el informe)**

- **`EI_EXPOSE_REP` / `EI_EXPOSE_REP2` en `domain` y `AccountSession`**: los getters y setters devuelven o guardan
  directamente objetos mutables (`List`, `Item`, `Product`, `Account`), así que desde fuera se puede modificar el estado
  interno sin pasar por la clase. Es un riesgo real de encapsulación, pero es el diseño JavaBean que necesitan MyBatis
  y las JSP; corregirlo con copias defensivas tendría coste y podría romper el mapeo.

**Falsos positivos (excluidos en `spotbugs-exclude.xml`)**

- **`EI_EXPOSE_REP2` en los constructores de `AccountService` y `OrderService`**: guardan el mapper que Spring les
  inyecta. Compartir ese objeto es precisamente lo que se busca con la inyección de dependencias.
- **`DLS_DEAD_LOCAL_STORE` en `CartController.updateCartQuantities`**: es la variable del `catch (NumberFormatException e)`,
  que se ignora a propósito (una cantidad no numérica simplemente no modifica el carrito).
