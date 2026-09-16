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
target/site/checkstyle-result.xml
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
* **AvoidStarImport**: evita los imports mediante `*`.
* **EmptyLineSeparator**: comprueba la separación mediante líneas vacías.
* **NeedBraces**: exige llaves en estructuras de control.
* **OneStatementPerLine**: permite una única sentencia por línea.
* **WhitespaceAround**: comprueba los espacios alrededor de operadores y estructuras.
* **ParameterName**: comprueba la nomenclatura de los parámetros.
* **LocalVariableName**: comprueba la nomenclatura de las variables locales.
