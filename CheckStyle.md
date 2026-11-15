# Informe de análisis estático con CheckStyle

## 1. Introducción

[CheckStyle](https://checkstyle.org/) es una herramienta de análisis estático que comprueba que el código
fuente Java cumple un conjunto de reglas de estilo, formato, nomenclatura y buenas prácticas. No ejecuta
el código: analiza los ficheros fuente y reporta cada incumplimiento indicando fichero, línea, columna y
regla incumplida.

En este proyecto (JPetStore 6) se ha integrado CheckStyle mediante el plugin de Maven
`maven-checkstyle-plugin` con una configuración de reglas propia.

| Elemento | Valor |
| --- | --- |
| Plugin Maven | `org.apache.maven.plugins:maven-checkstyle-plugin` **3.6.0** |
| Motor CheckStyle | `com.puppycrawl.tools:checkstyle` **14.1.0** |
| Fichero de reglas | `config/checkstyle/checkstyle.xml` |
| Código analizado | `src/main/java` (paquetes `org.mybatis.jpetstore.*`) |

---

## 2. Cambios realizados en el proyecto para implementarlo

| Archivo | Carpeta | Tipo de cambio | Descripción |
| --- | --- | --- | --- |
| `pom.xml` | raíz del proyecto | Modificado | Se añade el plugin `maven-checkstyle-plugin` dentro de `<build><pluginManagement><plugins>`. |
| `checkstyle.xml` | `config/checkstyle/` (carpeta nueva) | Creado | Define el conjunto de reglas (módulos) que aplica CheckStyle. |
| `CheckStyle.md` | raíz del proyecto | Creado | Este informe. |

**No se ha modificado ningún fichero de código fuente** (`src/main/java`, `src/test/java`): la
herramienta se ha integrado únicamente a nivel de configuración de build, y las violaciones detectadas
se documentan en este informe sin corregirlas.

### Fragmento añadido al `pom.xml`

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-checkstyle-plugin</artifactId>
  <version>3.6.0</version>

  <configuration>
    <configLocation>config/checkstyle/checkstyle.xml</configLocation>
  </configuration>

  <dependencies>
    <dependency>
      <groupId>com.puppycrawl.tools</groupId>
      <artifactId>checkstyle</artifactId>
      <version>14.1.0</version>
    </dependency>
  </dependencies>
</plugin>
```

- `configLocation` hace que el plugin use nuestras reglas en lugar de la configuración por defecto
  (`sun_checks.xml`).
- La dependencia explícita de `checkstyle` 14.1.0 fuerza una versión reciente del motor, ya que la que
  trae el plugin por defecto es más antigua y no soporta la sintaxis moderna de Java.
- El plugin está en `pluginManagement` y no está asociado a ninguna fase del ciclo de vida, por lo que
  **no se ejecuta en `mvn package`/`mvn verify`**: hay que invocarlo explícitamente (ver sección 4).

---

## 3. Reglas configuradas

Todas las reglas se definen en `config/checkstyle/checkstyle.xml`. Salvo `LineLength`, que es un módulo
del `Checker` (trabaja a nivel de fichero), el resto cuelgan del `TreeWalker` (trabajan sobre el árbol
sintáctico de cada clase Java). Todas usan sus valores por defecto excepto donde se indica.

### 3.1 Formato de línea

| Regla | Qué comprueba | Configuración |
| --- | --- | --- |
| `LineLength` | Longitud máxima de cada línea. | `max = 100` |

### 3.2 Javadoc

| Regla | Qué comprueba |
| --- | --- |
| `JavadocMethod` | Que el Javadoc de los métodos sea coherente con su firma (`@param`, `@return`, `@throws`). |
| `JavadocType` | Que el Javadoc de clases e interfaces esté bien formado. |
| `MissingJavadocType` | Que las clases e interfaces públicas tengan Javadoc. |

### 3.3 Imports

| Regla | Qué comprueba |
| --- | --- |
| `AvoidStarImport` | Prohíbe imports con comodín (`import java.util.*;`). |
| `UnusedImports` | Detecta imports que no se usan. |
| `CustomImportOrder` | Orden y agrupación de los imports (sin líneas en blanco extra dentro de un mismo grupo). |

### 3.4 Espaciado y formato

| Regla | Qué comprueba |
| --- | --- |
| `EmptyLineSeparator` | Línea en blanco entre `package`, imports, atributos, métodos, etc. |
| `NeedBraces` | Obliga a usar llaves `{}` en `if`, `else`, `for`, `while`, `do`. |
| `OneStatementPerLine` | Una única sentencia por línea. |
| `WhitespaceAround` | Espacios alrededor de operadores, llaves y palabras clave. |
| `WhitespaceAfter` | Espacio después de comas, punto y coma y *casts*. |

### 3.5 Nomenclatura

| Regla | Qué comprueba |
| --- | --- |
| `TypeName` | Clases e interfaces en `UpperCamelCase`. |
| `MethodName` | Métodos en `lowerCamelCase`. |
| `MemberName` | Atributos no estáticos en `lowerCamelCase`. |
| `ParameterName` | Parámetros en `lowerCamelCase`. |
| `LocalVariableName` | Variables locales en `lowerCamelCase`. |
| `ClassTypeParameterName` | Parámetros genéricos de clase con una letra mayúscula (`T`, `E`...). |

### 3.6 Visibilidad y diseño

| Regla | Qué comprueba |
| --- | --- |
| `VisibilityModifier` | Que los atributos no sean públicos (deben ser `private` y accederse por getters/setters). |
| `FinalClass` | Clases con solo constructores privados que deberían declararse `final`. |

### 3.7 Buenas prácticas de codificación

| Regla | Qué comprueba |
| --- | --- |
| `MagicNumber` | Números literales "mágicos" en el código (salvo -1, 0, 1, 2) que deberían ser constantes. |
| `EqualsHashCode` | Que si se sobrescribe `equals()` también se sobrescriba `hashCode()`. |
| `ReturnCount` | Número máximo de `return` por método (por defecto 2 en métodos no `void`, 1 en `void`). |

### 3.8 Métricas

| Regla | Qué comprueba |
| --- | --- |
| `MethodLength` | Longitud máxima de un método (por defecto 150 líneas). |
| `ClassFanOutComplexity` | Número máximo de clases de las que depende una clase (por defecto 20). |

### 3.9 Comentarios y expresiones regulares

| Regla | Qué comprueba |
| --- | --- |
| `TodoComment` | Comentarios que contienen `TODO:` (trabajo pendiente). |
| `Regexp` | Comprobación genérica mediante una expresión regular (con la configuración por defecto). |

---

## 4. Cómo ejecutarlo

Desde la raíz del proyecto (en Windows se usa `mvnw.cmd` en lugar de `./mvnw`).

**Generar el informe de violaciones** (no detiene el build):

```bash
./mvnw checkstyle:checkstyle
```

Genera:

- `target/checkstyle-result.xml`: resultado en XML con todas las violaciones.
- `target/site/checkstyle.html` (al ejecutarlo con `./mvnw site`): informe HTML navegable.

**Comprobar las reglas** (pensado para usarse como control de calidad):

```bash
./mvnw checkstyle:check
```

> Nota: el POM padre (`mybatis-parent`) configura `failOnViolation=false`, por lo que `checkstyle:check`
> muestra un aviso (`You have 246 Checkstyle violations`) pero el build termina en `BUILD SUCCESS`.
> Para que el build falle al encontrar violaciones:
>
> ```bash
> ./mvnw checkstyle:check -Dcheckstyle.failOnViolation=true
> ```

**Ver las violaciones por consola**:

```bash
./mvnw checkstyle:check -Dcheckstyle.consoleOutput=true
```

También se puede usar `mvn` en lugar de `./mvnw` si Maven está instalado en el sistema.

---

## 5. Resultados del análisis

### 5.1 Resumen general

| Métrica | Valor |
| --- | --- |
| Ficheros Java con violaciones | 25 (todos los de `src/main/java`) |
| Otros ficheros con violaciones | 3 (`.properties` de `target/` y `.mvn/`) |
| Violaciones totales | **246** |
| Severidad | Todas `error` (severidad por defecto) |

### 5.2 Violaciones por regla

| Regla | Nº violaciones | % |
| --- | ---: | ---: |
| `EmptyLineSeparator` | 111 | 45,1 % |
| `LineLength` | 105 | 42,7 % |
| `CustomImportOrder` | 15 | 6,1 % |
| `NeedBraces` | 11 | 4,5 % |
| `ReturnCount` | 4 | 1,6 % |
| **Total** | **246** | 100 % |

El resto de reglas configuradas (Javadoc, nomenclatura, `MagicNumber`, `EqualsHashCode`,
`VisibilityModifier`, `UnusedImports`, `AvoidStarImport`, métricas, `TodoComment`, etc.) **no
detectaron ninguna violación**: el código original de JPetStore ya respeta las convenciones de nombres,
tiene Javadoc en clases y métodos públicos y no usa imports con comodín ni atributos públicos.

### 5.3 Violaciones por paquete / carpeta

| Carpeta | EmptyLineSep. | LineLength | ImportOrder | NeedBraces | ReturnCount | Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `src/main/java/.../domain` | 78 | 4 | – | – | – | 82 |
| `src/main/java/.../web` (controllers + configuration) | 18 | 20 | 7 | 11 | 4 | 60 |
| `src/main/java/.../service` | 8 | 2 | 3 | – | – | 13 |
| `src/main/java/.../mapper` | 7 | – | 5 | – | – | 12 |
| `target/` (cachés del formatter e impsort) | – | 77 | – | – | – | 77 |
| `.mvn/wrapper/` | – | 2 | – | – | – | 2 |

### 5.4 Ficheros con más violaciones

| Fichero | Violaciones |
| --- | ---: |
| `web/controllers/OrderController.java` | 28 |
| `domain/Order.java` | 28 |
| `domain/Account.java` | 18 |
| `web/controllers/AccountController.java` | 15 |
| `domain/Item.java` | 12 |
| `domain/LineItem.java` | 8 |
| `web/controllers/CatalogController.java` | 6 |
| `web/controllers/CartController.java` | 6 |
| `service/OrderService.java` | 6 |
| Resto de ficheros Java (16) | 1 – 5 cada uno |

### 5.5 Análisis de cada tipo de violación

**`EmptyLineSeparator` (111).** Es la más frecuente. Se divide en:

- 86 casos `'VARIABLE_DEF' debe estar separado de la línea anterior`: atributos consecutivos sin línea en
  blanco entre ellos. Se concentra en las clases del paquete `domain`, donde cada atributo va precedido de
  su Javadoc pero pegado al anterior:

  ```java
  /** The username. */
  private String username;
  /** The password. */
  private String password;
  ```

- 25 casos `'package' debe estar separado de la línea anterior`: la sentencia `package` va justo después
  del comentario de licencia, sin línea en blanco. Aparece en todos los ficheros Java.

**`LineLength` (105).** Líneas de más de 100 caracteres:

- 26 en código Java, sobre todo en los controladores (firmas de métodos con `@RequestParam` largos) y en
  expresiones *stream* encadenadas, p. ej. `Cart.java:158` (108 caracteres):

  ```java
  .map(cartItem -> cartItem.getItem().getListPrice().multiply(new BigDecimal(cartItem.getQuantity())))
  ```

  El proyecto original de MyBatis usa un formateador con ancho de 120, por eso choca con el límite de 100.

- 79 son **falsos positivos**: al ser `LineLength` un módulo del `Checker`, se aplica a todos los ficheros
  que recibe el plugin, incluidos `target/formatter-maven-cache.properties`,
  `target/impsort-maven-cache.properties` y `.mvn/wrapper/maven-wrapper.properties`, que no son código.

**`CustomImportOrder` (15).** Mensaje `Separación extra en el grupo de importación antes '...'`: hay una
línea en blanco entre imports que la regla considera del mismo grupo (p. ej. entre `org.mybatis...` y
`org.mybatis.jpetstore.domain...` en todos los *mappers*, o entre imports de `javax.servlet` en los
controladores). Es una diferencia de criterio entre la agrupación del proyecto y la de la regla por
defecto.

**`NeedBraces` (11).** Todas en `OrderController.java` (líneas 163-183): una serie de `if` sin llaves
que copian campos del formulario al pedido de la sesión:

```java
if (order.getCardType() != null)
  sessionOrder.setCardType(order.getCardType());
```

Es la violación con más impacto real en la mantenibilidad, ya que añadir una segunda sentencia al `if`
sin poner llaves introduciría un error silencioso.

**`ReturnCount` (4).** Métodos con más de 2 `return`:

- `CartController.java:125` (`removeItemFromCart`): 3 `return`.
- `OrderController.java:98`, `:137` y `:221`.

Son métodos de controlador que devuelven distintas vistas según validaciones (*early return*); es un
patrón aceptable, pero indica métodos con varias responsabilidades.

### 5.6 Conclusiones

- La mayoría de las violaciones (≈ 88 %) son de **formato** (`EmptyLineSeparator` y `LineLength`), no
  afectan al comportamiento y se corregirían automáticamente con un formateador.
- Las reglas de **nomenclatura, Javadoc, visibilidad y buenas prácticas** no detectan problemas: el código
  base es de buena calidad en esos aspectos.
- Los problemas más relevantes están en la capa **web**, en especial `OrderController.java`, que acumula
  `NeedBraces`, `ReturnCount` y líneas largas.
- Posibles mejoras de la configuración:
  - Limitar `LineLength` a ficheros Java (`<property name="fileExtensions" value="java"/>`) para eliminar
    los 79 falsos positivos de `target/` y `.mvn/`.
  - Ajustar `max` de `LineLength` a 120 para alinearlo con el formateador del proyecto, o aplicar el
    formateador con 100.
  - Configurar `EmptyLineSeparator` con `allowMultipleEmptyLines`/`tokens` si no se quiere exigir línea
    en blanco entre atributos.
  - Asociar el plugin a la fase `validate` en `<plugins>` (no solo en `pluginManagement`) para que se
    ejecute automáticamente en cada build.
