## CheckStyle

Las reglas de CheckStyle se encuentran definidas en:

```text
config/checkstyle/checkstyle.xml
```

### Ejecución

Para ejecutar la comprobación:

```bash
./mvnw checkstyle:check
```

Este comando comprueba las reglas configuradas y genera el resultado en:

```text
target/checkstyle-result.xml
```

También se puede ejecutar mediante Maven:

```bash
mvn checkstyle:check
```

Para generar el informe de CheckStyle:

```bash
./mvnw checkstyle:checkstyle
```

### Reglas aplicadas

* **LineLength**: longitud máxima de 100 caracteres por línea.

* **JavadocMethod**: comprueba la documentación Javadoc de los métodos.

* **JavadocType**: comprueba la documentación Javadoc de clases e interfaces.

* **MissingJavadocType**: detecta clases e interfaces que no tienen documentación Javadoc.

* **AvoidStarImport**: evita los imports mediante `*`.

* **UnusedImports**: detecta imports que no se utilizan.

* **CustomImportOrder**: comprueba que los imports sigan el orden establecido.

* **EmptyLineSeparator**: comprueba la separación mediante líneas vacías.

* **NeedBraces**: exige llaves en estructuras de control.

* **OneStatementPerLine**: exige una única sentencia por línea.

* **WhitespaceAround**: comprueba los espacios alrededor de operadores y estructuras.

* **WhitespaceAfter**: comprueba los espacios después de determinados elementos.

* **TypeName**: comprueba la nomenclatura de las clases.

* **MethodName**: comprueba la nomenclatura de los métodos.

* **MemberName**: comprueba la nomenclatura de los atributos.

* **ParameterName**: comprueba la nomenclatura de los parámetros.

* **LocalVariableName**: comprueba la nomenclatura de las variables locales.

* **ClassTypeParameterName**: comprueba la nomenclatura de los parámetros de tipo de las clases.

* **VisibilityModifier**: comprueba que los miembros tengan un modificador de visibilidad adecuado.

* **MagicNumber**: detecta el uso de números mágicos en el código.

* **EqualsHashCode**: comprueba que las clases que sobrescriben `equals()` también sobrescriban `hashCode()`.

* **ReturnCount**: limita el número de sentencias `return` de un método.

* **FinalClass**: detecta clases que podrían declararse como `final`.

* **MethodLength**: limita la longitud de los métodos.

* **ClassFanOutComplexity**: controla el número de clases de las que depende una clase.

* **TodoComment**: detecta comentarios que contienen `TODO`.

* **Regexp**: permite comprobar patrones mediante expresiones regulares.
