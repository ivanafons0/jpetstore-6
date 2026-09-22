## PIT (Mutation Testing)

PIT es una herramienta de **mutación de código** que permite evaluar la calidad de las pruebas. Modifica temporalmente el código y comprueba si los tests son capaces de detectar dichos cambios.

### Configuración

La configuración de PIT se encuentra en:

```text
pom.xml
```

Se configuró el plugin `pitest-maven` y se limitó la ejecución a los tests unitarios del proyecto.

Además, se excluyeron las pruebas de integración de interfaz gráfica/navegador, como:

```text
ScreenTransitionIT
```

y se limitaron los tests utilizados por PIT al patrón:

```text
org.mybatis.jpetstore.*Test
```

Esto evita que PIT intente ejecutar pruebas de integración que no son adecuadas para el análisis de mutaciones.

### Ejecución

Para ejecutar PIT:

```bash
./mvnw org.pitest:pitest-maven:mutationCoverage
```

En Windows:

```powershell
.\mvnw.cmd org.pitest:pitest-maven:mutationCoverage
```

También se puede ejecutar con Maven:

```bash
mvn org.pitest:pitest-maven:mutationCoverage
```

Para obtener información más detallada durante la ejecución:

```powershell
.\mvnw.cmd org.pitest:pitest-maven:mutationCoverage -Dverbose=true
```

### Informe

El informe HTML generado por PIT se encuentra en:

```text
target/pit-reports/
```

Dentro de esta carpeta se genera un directorio con el informe de la ejecución. El archivo principal es:

```text
target/pit-reports/index.html
```

El informe muestra, entre otros datos:

* **Mutaciones generadas**: cambios realizados automáticamente sobre el código.
* **Killed**: mutaciones detectadas por los tests.
* **Survived**: mutaciones que los tests no detectaron.
* **Mutation Score**: porcentaje de mutaciones eliminadas por los tests.

### Resultado actual

En la ejecución realizada se obtuvieron:

```text
Generated 271 mutations
Killed 222 (82%)
BUILD SUCCESS
```

Por tanto, la ejecución de PIT finalizó correctamente con un **82 % de mutation score**.
