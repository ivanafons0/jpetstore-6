# SpotBugs en JPetStore 6 — guía completa

Documento de apoyo para entender y defender la implementación de SpotBugs en este proyecto.
Complementa a `SpotBugs.md`, que es el resumen corto de la rama `SpotBugs`.

---

## 1. Qué es SpotBugs

SpotBugs es un **analizador estático de errores para Java**. Lee las clases **ya compiladas** (`.class`), no el
código fuente, y busca **patrones de error** (*bug patterns*): formas de escribir código que, por experiencia,
suelen esconder un fallo.

No ejecuta el programa, no necesita tests y no comprueba estilo: su pregunta es **"¿puede fallar esto?"**.

La versión usada en este proyecto es la **4.9.8** (a través del plugin de Maven `spotbugs-maven-plugin:4.9.8.3`),
y trae:

| Elemento | Cantidad |
|---|---|
| **Detectores** (los analizadores que recorren el código) | **190** (8 desactivados por defecto) |
| **Patrones de error** (los tipos de aviso que puede dar) | **507** |
| **Categorías** | 10 |

> ⚠️ **Corrección importante.** Durante el desarrollo hablamos de "más de 400 detectores". El número correcto es
> **190 detectores**, que entre todos pueden reportar **507 patrones de error distintos**. Confundir detector con
> patrón es justo el tipo de detalle que un profesor puede preguntar, así que conviene tenerlo claro:
> **un detector puede reportar varios patrones**. Por ejemplo, el detector `FindReturnRef` reporta los patrones
> `EI_EXPOSE_REP` y `EI_EXPOSE_REP2`, que son dos de los que aparecen en este proyecto.

### De dónde surge

1. **FindBugs (2003–2015)**. Nace como proyecto de investigación en la **Universidad de Maryland**, de la mano de
   **David Hovemeyer** y **William Pugh**. Su artículo fundacional, *"Finding Bugs is Easy"*, presentado en OOPSLA
   en 2004, defiende una idea muy pragmática: no hace falta demostrar formalmente que un programa es correcto para
   encontrar fallos; basta con buscar **patrones sospechosos** concretos, aceptando que habrá algún falso positivo.
   Esa idea es la base de la herramienta y explica sus virtudes y sus límites.
2. **Abandono**. La última versión de FindBugs, la 3.0.1, es de **2015**, y después el proyecto quedó parado.
3. **SpotBugs (2016 en adelante)**. La comunidad crea un **fork** que continúa el proyecto con el nombre SpotBugs,
   manteniendo los patrones, los códigos y el formato de los filtros de FindBugs. Por eso aún hoy el XML de
   exclusiones se llama `FindBugsFilter` y las clases internas están en paquetes `edu.umd.cs.findbugs`: son
   herencia directa de la herramienta original.
4. Licencia **LGPL**, proyecto activo, con integración en Maven, Gradle, Eclipse, IntelliJ, SonarQube y GitHub.

Saber esto importa para la defensa porque explica dos cosas: **por qué la nomenclatura mezcla los dos nombres** y
**por qué la herramienta asume que tendrá falsos positivos** (su diseño los acepta a cambio de encontrar fallos
reales con poco esfuerzo).

### Clasificación para la asignatura

| Eje | SpotBugs | Por qué |
|---|---|---|
| Estática / dinámica | **Estática** | Analiza el código sin ejecutarlo: no hay que lanzar la aplicación ni los tests |
| Caja blanca / negra | **Caja blanca** | Trabaja sobre la estructura interna del código (bytecode, flujo de control y de datos), no sobre entradas y salidas |
| Funcional / no funcional | **Funcional** | Busca defectos que afectan a lo que el programa **hace**: excepciones, comparaciones imposibles, estado corrompible… |

**Comparación con las otras herramientas del grupo** (esto es lo que suele preguntarse):

| Herramienta | Qué lee | Qué pregunta | Aspecto |
|---|---|---|---|
| **CheckStyle** | Código **fuente** (`.java`), construyendo su árbol sintáctico | ¿Está **bien escrito**? | No funcional (estilo, mantenibilidad) |
| **SpotBugs** | **Bytecode** (`.class`) | ¿Puede **fallar**? | Funcional (defectos) |
| **ArchUnit** | Bytecode, pero con reglas que escribes tú y se ejecutan como tests | ¿Está **bien organizado**? | Estructural |
| **jqwik** | Nada: **ejecuta** el programa con datos aleatorios | ¿Se comporta bien con **cualquier dato**? | Funcional (caja negra) |
| **JETM** | **Ejecuta** y cronometra | ¿Es **rápido**? | No funcional (rendimiento) |

Las tres primeras son estáticas o semiestáticas y de caja blanca, pero **miran cosas distintas**: SpotBugs y
CheckStyle son complementarias, no alternativas. CheckStyle nunca te dirá que vas a tener un `NullPointerException`,
y SpotBugs nunca te dirá que te falta un espacio o un Javadoc.

---

## 2. Cómo funciona por dentro

Es la parte que más puntos da en una defensa, porque demuestra que no es "darle a un botón".

### 2.1 Por qué bytecode y no código fuente

SpotBugs lee los `.class` con la librería **ASM**. Ventajas:

- **Analiza lo que realmente se ejecuta**, incluido lo que el compilador genera por ti: *autoboxing*,
  concatenación de `String`, clases sintéticas de las *lambdas*, el `StringBuilder` que crea un `+` dentro de un
  bucle…
- **No necesita que el código sea compilable en su entorno**: le basta el resultado de la compilación, con lo que
  no depende de la configuración del proyecto ni del IDE.
- Puede analizar **librerías de terceros** sin tener sus fuentes.

Inconvenientes:

- **Hay que compilar antes.** De ahí que el comando sea `./mvnw compile spotbugs:check` y no solo `spotbugs:check`.
- **Pierde los comentarios y el formato**, así que no puede opinar de estilo: ese hueco lo cubre CheckStyle.
- Las líneas que te indica vienen de la **tabla de depuración** del `.class`, así que pueden desplazarse un poco
  respecto al fuente si se compila sin información de depuración.

### 2.2 Qué hace con cada clase

1. **Construye el grafo de flujo de control** (*control flow graph*) de cada método: los bloques de instrucciones y
   los saltos entre ellos.
2. **Hace análisis de flujo de datos** (*dataflow analysis*) sobre ese grafo. Propaga información por todos los
   caminos posibles, por ejemplo:
   - *análisis de tipos*: qué tipo real puede tener cada valor de la pila en cada punto;
   - *análisis de nulos*: en qué puntos un valor **puede** ser `null`;
   - *numeración de valores* (*value numbering*): qué expresiones son con seguridad el mismo valor.
3. **Pasa los detectores**. Cada detector es un **visitante** (*visitor*) que recorre clases, campos, métodos e
   instrucciones y, cuando reconoce su patrón, emite un aviso. De ahí que en el `pom.xml` la lista de detectores se
   configure con una etiqueta llamada **`<visitors>`**: es el término interno de la herramienta.
4. **Prioriza y filtra**: a cada aviso le asigna una **confianza** y un **rank**, y después aplica los filtros
   (umbral de confianza, filtros XML de exclusión o inclusión).

Como el análisis recorre **todos los caminos posibles sin ejecutarlos**, puede avisar de un camino que en la
práctica nunca se da (**falso positivo**) y puede no ver un fallo que requiere razonar sobre la lógica de negocio
(**falso negativo**). Esto no es un defecto de implementación: por el **teorema de Rice**, decidir
automáticamente y siempre si un programa tiene un fallo de comportamiento es **indecidible**. Toda herramienta de
análisis estático tiene que elegir entre avisar de más o avisar de menos. SpotBugs elige equilibrar las dos cosas
patrón a patrón, y por eso cada aviso trae una confianza.

### 2.3 Anatomía de un aviso

Este es un aviso real del proyecto:

```
Medium: org.mybatis.jpetstore.domain.Cart.getCartItemList() may expose internal
representation by returning Cart.itemList
[org.mybatis.jpetstore.domain.Cart] At Cart.java:[line 55] EI_EXPOSE_REP
```

| Parte | Valor en el ejemplo | Qué es |
|---|---|---|
| **Confianza** | `Medium` | Lo seguro que está SpotBugs de que es un fallo: `High`, `Medium` o `Low`. Internamente es la *priority* 1, 2 o 3 del XML |
| **Mensaje** | *may expose internal representation…* | Explicación del patrón aplicada a este caso |
| **Clase, fichero y línea** | `Cart`, `Cart.java:55` | Dónde está |
| **Patrón** | `EI_EXPOSE_REP` | El tipo de error: es el código que hay que citar siempre |
| **Rank** (no sale en consola, sí en el XML y la GUI) | 18 | Gravedad estimada, de 1 a 20 |

**Confianza y rank son cosas distintas**, y conviene no mezclarlas:

- **Confianza**: *"¿me estoy equivocando al avisar?"*
- **Rank**: *"si tengo razón, ¿qué daño hace?"* Se agrupa en: **1–4 *scariest***, **5–9 *scary***,
  **10–14 *troubling*** y **15–20 *of concern***.

En este proyecto **todos los avisos tienen rank 17–20**, es decir, la banda menos grave. Eso ya es un resultado
que se puede defender: *el código de JPetStore no tiene errores graves según SpotBugs; lo que aparece son riesgos
de diseño y dos fallos reales pero de impacto limitado*.

### 2.4 Nomenclatura de los códigos

Los códigos no son aleatorios: el **prefijo** indica la familia del patrón. Los que aparecen aquí:

| Prefijo | Significado | Ejemplo en el proyecto |
|---|---|---|
| `EI_` | *Exposed Internal representation* | `EI_EXPOSE_REP`, `EI_EXPOSE_REP2` |
| `DM_` | *Dubious Method invocation* | `DM_CONVERT_CASE` |
| `DLS_` | *Dead Local Store* | `DLS_DEAD_LOCAL_STORE` |
| `UWF_` | *Unwritten Field* | `UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR` |

Otros prefijos habituales que conviene conocer: `NP_` (*null pointer*), `EQ_` (`equals`), `HE_` (`hashCode`/`equals`),
`RV_` (*return value* ignorado), `SQL_`, `OS_`/`ODR_` (*streams* y recursos sin cerrar), `IS_`/`IC_` (concurrencia).

### 2.5 Categorías

SpotBugs agrupa los patrones en 10 categorías. Las relevantes:

| Categoría | Qué recoge | ¿Aparece aquí? |
|---|---|---|
| `CORRECTNESS` | Código que casi con seguridad no hace lo que el programador quería | No |
| `BAD_PRACTICE` | Incumplimiento de buenas prácticas (`equals` sin `hashCode`, excepciones tragadas…) | No |
| `MALICIOUS_CODE` | Código manipulable desde fuera por exponer estado mutable | **Sí (13)** |
| `MT_CORRECTNESS` | Errores de concurrencia | No |
| `PERFORMANCE` | Ineficiencias | No |
| `STYLE` (*dodgy code*) | Código sospechoso o confuso, aunque no falle | **Sí (1)** |
| `I18N` | Internacionalización | **Sí (2)** |
| `SECURITY` | Seguridad (se amplía mucho con el plugin FindSecBugs) | No |
| `EXPERIMENTAL`, `NOISE` | Patrones en pruebas y ruido; apagados o descartables | No |

El nombre `MALICIOUS_CODE` suena fuerte, pero significa *"código que podría ser manipulado por un atacante"*,
no *"código malicioso"*. Es la categoría de la que viene la mayoría de los avisos de JPetStore.

---

## 3. Cómo se activa (y cómo se haría desde cero)

### 3.1 Formas de ejecutarlo

| Forma | Cuándo se usa |
|---|---|
| **Plugin de Maven** | Es lo que usamos: integra el análisis en el ciclo de vida del proyecto |
| Plugin de Gradle | Equivalente para proyectos Gradle |
| **GUI propia** (`spotbugs:gui`) | Navegar por los avisos de forma interactiva: ideal para explorar y para capturas |
| Línea de comandos / aplicación independiente | Analizar un `.jar` suelto sin proyecto |
| Plugins de Eclipse e IntelliJ | Ver los avisos en el editor mientras programas |
| SonarQube o GitHub Code Scanning | Integración en un panel de calidad o en los *pull requests* |

### 3.2 Desde cero, en un proyecto sin nada heredado

Si JPetStore no heredase nada, bastaría con **esto** en el `pom.xml`:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>com.github.spotbugs</groupId>
      <artifactId>spotbugs-maven-plugin</artifactId>
      <version>4.9.8.3</version>
      <configuration>
        <effort>Max</effort>                 <!-- análisis más profundo -->
        <threshold>Low</threshold>           <!-- mostrar también la confianza media y baja -->
        <includeTests>false</includeTests>   <!-- analizar src/main, no los tests -->
        <xmlOutput>true</xmlOutput>          <!-- generar el informe XML -->
        <failOnError>false</failOnError>     <!-- que los avisos no rompan el build -->
        <excludeFilterFile>config/spotbugs/spotbugs-exclude.xml</excludeFilterFile>
      </configuration>
      <executions>
        <execution>
          <id>spotbugs-check</id>
          <phase>verify</phase>              <!-- opcional: análisis automático en cada build -->
          <goals>
            <goal>check</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Y se ejecutaría así:

```bash
mvn compile spotbugs:check      # analiza y escribe los avisos en consola
mvn compile spotbugs:spotbugs   # genera el informe XML
mvn compile spotbugs:gui        # abre la interfaz gráfica
mvn verify                      # si se ha declarado la ejecución atada a verify
```

> En **este** proyecto hay que usar **`./mvnw`** en lugar de `mvn`, porque el `pom.xml` exige Maven 3.9.16 o
> superior y el Maven instalado en el equipo es más antiguo. El *wrapper* (`mvnw`) descarga la versión correcta.

### 3.3 Las opciones de configuración que hay que saber explicar

| Opción | Valores | Qué hace |
|---|---|---|
| `effort` | `Min`, `Default`, `Max` | Profundidad del análisis. `Max` activa análisis más costosos (por ejemplo, seguir valores entre métodos) a cambio de tardar más |
| `threshold` | `High`, `Default`, `Low`, `Exp` | **Filtra por confianza.** `High` solo muestra los avisos de confianza alta; `Low` los muestra todos |
| `visitors` | lista de detectores | Si se rellena, **solo** se ejecutan esos detectores. Si está vacía, se ejecutan todos |
| `omitVisitors` | lista de detectores | Lo contrario: ejecuta todos menos esos |
| `includeTests` | `true`/`false` (por defecto `false`) | Analizar o no el código de pruebas |
| `onlyAnalyze` | paquetes o clases | Restringir el análisis a una parte del proyecto |
| `excludeFilterFile` / `includeFilterFile` | ruta a un XML | Excluir avisos concretos (falsos positivos) o analizar solo los indicados |
| `maxRank` | 1–20 | Descartar los avisos menos graves |
| `failOnError` | `true`/`false` | Si el objetivo `check` debe romper el build al encontrar avisos |
| `relaxed` | `true`/`false` | Modo relajado: no aplica algunos filtros heurísticos internos, así que da más avisos y más falsos positivos |
| `plugins` | dependencias | Añadir conjuntos de patrones externos: **FindSecBugs** (seguridad) o **fb-contrib** |
| `sarifOutput` | `true`/`false` | Informe en formato SARIF, el que consume GitHub Code Scanning |

---

## 4. Qué había en este proyecto y por qué estaba mal

Este es **el núcleo de la defensa**, porque es el trabajo real que hicimos: no fue añadir la herramienta, fue
**descubrir que estaba puesta pero neutralizada** y arreglarlo.

### 4.1 La herencia de Maven

El `pom.xml` de JPetStore **hereda** de otro POM:

```xml
<parent>
  <groupId>org.mybatis</groupId>
  <artifactId>mybatis-parent</artifactId>
  <version>52</version>
</parent>
```

`mybatis-parent` es la **plantilla común** de todos los proyectos de MyBatis: fija versiones de plugins,
convenciones y configuración compartida. Entre otras cosas, ya traía SpotBugs configurado así (líneas 431–445 de
`mybatis-parent-52.pom`):

```xml
<plugin>
  <groupId>com.github.spotbugs</groupId>
  <artifactId>spotbugs-maven-plugin</artifactId>
  <version>${spotbugs.plugin}</version>        <!-- 4.9.8.3 -->
  <configuration>
    <xmlOutput>true</xmlOutput>
    <xmlOutputDirectory>${project.build.directory}/spotbugs-reports</xmlOutputDirectory>
    <spotbugsXmlOutputDirectory>${project.build.directory}/spotbugs-reports</spotbugsXmlOutputDirectory>
    <threshold>High</threshold>
    <effort>Max</effort>
    <visitors>FindDeadLocalStores,UnreadFields</visitors>
    <onlyAnalyze>${spotbugs.onlyAnalyze}</onlyAnalyze>
    <relaxed>true</relaxed>
  </configuration>
</plugin>
```

### 4.2 Por qué eso era inservible para la práctica

Dos opciones lo dejaban casi apagado:

1. **`<visitors>FindDeadLocalStores,UnreadFields</visitors>`**
   Al rellenar esa lista, SpotBugs ejecuta **solo esos 2 detectores de los 190**. Es decir, de los 507 patrones
   posibles solo podían aparecer los que reportan esos dos detectores: variables que se asignan y no se usan, y
   campos que no se leen o no se inicializan. **Todo lo demás —nulos, concurrencia, rendimiento, seguridad,
   internacionalización, encapsulación— ni se miraba.**

2. **`<threshold>High</threshold>`**
   Con ese umbral solo se muestran los avisos de **confianza alta**. Los de confianza media y baja se descartan.

**La prueba de que el diagnóstico es correcto:** los 2 únicos avisos que salían al principio eran
`DLS_DEAD_LOCAL_STORE` y `UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR`, que son **exactamente** los patrones que
reportan `FindDeadLocalStores` y `UnreadFields`. No era que el proyecto estuviese limpio: era que la herramienta
solo buscaba dos cosas.

Y el dato más importante para la defensa: **los 3 fallos reales que encontramos tienen confianza baja**
(*priority* 3). Con `threshold=High`, e incluso con el valor por defecto `Default`, **no habrían aparecido nunca**.

### 4.3 ¿Por qué lo tenía así MyBatis? (hay que saber responderlo)

No es un error de los desarrolladores de MyBatis: es una decisión razonable **para ellos**. Mantienen muchos
proyectos con integración continua y les interesa que el análisis sea **rápido y sin ruido**, vigilando solo un par
de patrones que consideran siempre accionables. Para un uso así, limitar detectores y subir el umbral tiene
sentido.

Pero nuestro objetivo es el contrario: **aplicar la herramienta a fondo y analizar lo que encuentra**. Por eso
nuestra configuración no "corrige" a MyBatis, sino que **adapta la herramienta al objetivo de la práctica**. Esa
frase es, literalmente, la justificación del cambio.

---

## 5. Qué hicimos, paso a paso

Reproducible de principio a fin:

### Paso 1 — Rama propia

```bash
git switch -c SpotBugs master
```

Cada herramienta en su rama, como el resto del grupo.

### Paso 2 — Ver el punto de partida

```bash
./mvnw compile spotbugs:spotbugs
```

Resultado: **2 avisos**. El informe queda en `target/spotbugs-reports/spotbugsXml.xml`.

### Paso 3 — Averiguar por qué solo 2

Inspección del POM padre en el repositorio local de Maven
(`~/.m2/repository/org/mybatis/mybatis-parent/52/mybatis-parent-52.pom`) → aparecen `visitors` y `threshold`
descritos en el apartado 4.

### Paso 4 — Activar la herramienta de verdad

Bloque añadido al `pom.xml`, dentro de `<build><pluginManagement><plugins>`:

```xml
<plugin>
  <groupId>com.github.spotbugs</groupId>
  <artifactId>spotbugs-maven-plugin</artifactId>
  <configuration>
    <!-- mybatis-parent solo activa 2 detectores y avisos de confianza alta: aquí se usan todos -->
    <visitors combine.self="override" />
    <threshold>Low</threshold>
    <effort>Max</effort>
    <failOnError>false</failOnError>
    <excludeFilterFile>config/spotbugs/spotbugs-exclude.xml</excludeFilterFile>
  </configuration>
</plugin>
```

**Línea por línea, con su justificación:**

| Línea | Qué hace | Por qué |
|---|---|---|
| `<visitors combine.self="override" />` | **Vacía** la lista de detectores heredada | Sin lista, SpotBugs ejecuta **los 190 detectores**. El atributo es la clave: ver apartado 5.1 |
| `<threshold>Low</threshold>` | Muestra confianza alta, media y baja | Sin esto no se verían los 3 fallos reales, que son de confianza baja |
| `<effort>Max</effort>` | Análisis profundo | Ya venía del padre; se repite para que la configuración de nuestro proyecto sea **explícita y autocontenida** |
| `<failOnError>false</failOnError>` | Los avisos no rompen el build | Son 16 avisos que no vamos a corregir; si rompiesen el build, ningún compañero podría compilar el proyecto |
| `<excludeFilterFile>` | Ruta del filtro de falsos positivos | Ver paso 6 |

### 5.1 El detalle fino: `combine.self="override"`

Cuando un POM hijo y su padre configuran el mismo plugin, Maven **fusiona** las dos configuraciones (las mezcla
con las reglas de fusión XML de Plexus). Si yo hubiese escrito simplemente:

```xml
<visitors />
```

Maven habría **mantenido el valor del padre**, porque una etiqueta vacía no sustituye por sí sola al valor
heredado. El atributo `combine.self="override"` le dice: *"no fusiones esta etiqueta con la del padre,
**sustitúyela**"*. Con eso la lista queda realmente vacía y se activan todos los detectores.

Existe también `combine.children="append"`, que sirve para lo contrario: añadir elementos a una lista heredada en
vez de reemplazarla.

**Alternativa que también habría funcionado:** usar `<omitVisitors>` o redefinir la propiedad del padre. Elegimos
`combine.self="override"` porque es la forma estándar de anular configuración heredada y deja constancia explícita
en el POM de lo que se está haciendo, con el comentario al lado.

### 5.2 ¿Por qué en `<pluginManagement>` y no en `<plugins>`?

| Sitio | Efecto |
|---|---|
| `<plugins>` | El plugin **se ejecuta** en cada build, en la fase que tenga atada |
| `<pluginManagement>` | Solo **se configura**. Se ejecuta cuando lo invocas a mano (`./mvnw spotbugs:check`) |

Elegimos `pluginManagement` por tres razones:

1. **No ralentizar el build de los compañeros**: `effort=Max` sobre todas las clases añade tiempo a cada
   compilación.
2. **Coherencia con el grupo**: el compañero que hizo CheckStyle lo configuró igual, y así las dos herramientas se
   usan del mismo modo.
3. **Es un análisis para revisar e interpretar**, no una puerta de calidad que deba bloquear el build.

Si el profesor prefiere lo contrario (que el análisis se ejecute siempre), el cambio es mover el bloque a
`<plugins>` y añadir la `<execution>` atada a `verify` que aparece en el apartado 3.2. Conviene tener preparada
esa respuesta.

### Paso 5 — Ejecutar con todo activado

```bash
./mvnw compile spotbugs:spotbugs
```

Resultado: **20 avisos**, en 5 patrones distintos y 4 categorías.

### Paso 6 — Revisar uno por uno y filtrar solo los falsos positivos

Fichero nuevo `config/spotbugs/spotbugs-exclude.xml` (misma estructura que `config/checkstyle/` del compañero):

```xml
<FindBugsFilter ...>

  <!-- Los servicios guardan el mapper que Spring les inyecta por constructor: compartirlo es intencionado -->
  <Match>
    <Class name="~org\.mybatis\.jpetstore\.service\..*Service" />
    <Method name="&lt;init&gt;" />
    <Bug pattern="EI_EXPOSE_REP2" />
  </Match>

  <!-- La variable del catch (NumberFormatException) se ignora a propósito: una cantidad no numérica no cambia el carrito -->
  <Match>
    <Class name="org.mybatis.jpetstore.web.controllers.CartController" />
    <Method name="updateCartQuantities" />
    <Bug pattern="DLS_DEAD_LOCAL_STORE" />
  </Match>

</FindBugsFilter>
```

Detalles del formato que conviene saber explicar:

- Un `<Match>` es una **conjunción**: se excluye el aviso que cumple **todas** las condiciones de dentro. Así el
  filtro es quirúrgico: no apaga el patrón `EI_EXPOSE_REP2` en todo el proyecto, solo en los constructores de los
  servicios.
- El prefijo `~` convierte el valor en una **expresión regular**: `~org\.mybatis\.jpetstore\.service\..*Service`.
- `<init>` es el nombre que tiene el **constructor** en el bytecode (y hay que escribirlo con entidades XML,
  `&lt;init&gt;`, porque `<` no es válido dentro de un atributo).
- La etiqueta raíz se llama `FindBugsFilter` por la herencia histórica de FindBugs.

Alternativa que **no** usamos: la anotación `@SuppressFBWarnings` en el propio código. Se descartó porque obliga a
**modificar el código fuente** de la aplicación (y a añadir la dependencia `spotbugs-annotations`), mientras que un
filtro externo deja el código intacto y centraliza las justificaciones en un único fichero revisable.

### Paso 7 — Comprobar el resultado

```bash
./mvnw compile spotbugs:check
```

```
[INFO] BugInstance size is 16
[INFO] Total bugs: 16
[INFO] BUILD SUCCESS
```

### Paso 8 — Documentar

`SpotBugs.md` con la configuración, los comandos, la tabla de resultados y el análisis.

### Resumen del recorrido

```
Configuración heredada de mybatis-parent  →   2 avisos   (2 de 190 detectores, solo confianza alta)
Con los 190 detectores y threshold=Low    →  20 avisos
Tras filtrar 4 falsos positivos           →  16 avisos   ← los que se analizan
```

### Ficheros de la rama `SpotBugs`

| Fichero | Estado | Contenido |
|---|---|---|
| `pom.xml` | modificado | 12 líneas: el bloque de configuración del plugin |
| `config/spotbugs/spotbugs-exclude.xml` | nuevo | Filtro de falsos positivos, con justificación por exclusión |
| `SpotBugs.md` | nuevo | Documentación y análisis |

---

## 6. Alcance del análisis

| Qué | ¿Analizado? | Por qué |
|---|---|---|
| Las 26 clases de `src/main/java` (1.026 métodos) | ✅ | Es el código de la aplicación. Son 25 ficheros `.java`: la clase 26 es la clase interna `AccountSession` de `AccountController` |
| Los 5 paquetes (`domain`, `mapper`, `service`, `web.controllers`, `web.configuration`) | ✅ | Ninguno queda fuera |
| Los 23 ficheros de test | ❌ | `includeTests` está en `false` (su valor por defecto): lo que se verifica es la aplicación, no las pruebas. Se activaría con `<includeTests>true</includeTests>` |
| Las JSP de `src/main/webapp` | ❌ | SpotBugs lee bytecode, y las JSP son plantillas. Solo se analizarían si se precompilasen a *servlets* |
| Los XML de los mappers de MyBatis | ❌ | No es Java. El SQL de esos ficheros queda fuera del alcance de la herramienta |

Es importante decirlo así en la defensa: **dentro de lo que SpotBugs puede analizar, se analizó el 100 %**.

---

## 7. Los 20 avisos, uno a uno

### 7.1 Tabla completa

| # | Patrón | Clase | Método | Línea | Categoría | Rank | Confianza | Veredicto |
|---|---|---|---|---|---|---|---|---|
| 1 | `DM_CONVERT_CASE` | `CatalogService` | `searchProductList` | 114 | I18N | 20 | Baja | 🔴 **Fallo real** |
| 2 | `DM_CONVERT_CASE` | `CatalogController` | `searchProducts` | 132 | I18N | 20 | Baja | 🔴 **Fallo real** |
| 3 | `UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR` | `Order` | `getFormattedOrderDate` | 678 | STYLE | 20 | Baja | 🔴 **Fallo real** |
| 4 | `EI_EXPOSE_REP` | `Cart` | `getCartItemList` | 55 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| 5 | `EI_EXPOSE_REP` | `CartItem` | `getItem` | 73 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| 6 | `EI_EXPOSE_REP2` | `CartItem` | `setItem` | 83 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| 7 | `EI_EXPOSE_REP` | `Item` | `getProduct` | 98 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| 8 | `EI_EXPOSE_REP2` | `Item` | `setProduct` | 108 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| 9 | `EI_EXPOSE_REP` | `LineItem` | `getItem` | 159 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| 10 | `EI_EXPOSE_REP2` | `LineItem` | `setItem` | 169 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| 11 | `EI_EXPOSE_REP` | `Order` | `getLineItems` | 600 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| 12 | `EI_EXPOSE_REP2` | `Order` | `setLineItems` | 591 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| 13 | `EI_EXPOSE_REP` | `AccountController$AccountSession` | `getAccount` | 221 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| 14 | `EI_EXPOSE_REP` | `AccountController$AccountSession` | `getMyList` | 230 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| 15 | `EI_EXPOSE_REP2` | `AccountController$AccountSession` | `<init>` | 210 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| 16 | `EI_EXPOSE_REP2` | `AccountController$AccountSession` | `<init>` | 211 | MALICIOUS_CODE | 18 | Media | 🟡 Discutible |
| — | `EI_EXPOSE_REP2` | `AccountService` | `<init>` | 41 | MALICIOUS_CODE | 18 | Media | ⚪ Falso positivo (excluido) |
| — | `EI_EXPOSE_REP2` | `OrderService` | `<init>` | 62 | MALICIOUS_CODE | 18 | Media | ⚪ Falso positivo (excluido) |
| — | `EI_EXPOSE_REP2` | `OrderService` | `<init>` | 64 | MALICIOUS_CODE | 18 | Media | ⚪ Falso positivo (excluido) |
| — | `DLS_DEAD_LOCAL_STORE` | `CartController` | `updateCartQuantities` | 167 | STYLE | 17 | Media | ⚪ Falso positivo (excluido) |

Recuento final: **16 avisos** = 3 fallos reales + 13 discutibles. Excluidos: 4.

### 7.2 Los 3 fallos reales

#### `DM_CONVERT_CASE` — cambiar de mayúsculas sin indicar el idioma

**Dónde:** `CatalogService.searchProductList` (línea 114) y `CatalogController.searchProducts` (línea 132).

```java
// CatalogService
products.addAll(productMapper.searchProductList("%" + keyword.toLowerCase() + "%"));
```

**El problema.** `toLowerCase()` sin argumento usa el **idioma por defecto del sistema**, no un idioma fijo. El
caso clásico es el **turco**: en turco la `I` mayúscula se convierte en `ı` (i **sin punto**), no en `i`. Así que
en un servidor configurado en turco:

```java
"FISH".toLowerCase()  →  "fısh"   // en turco
"FISH".toLowerCase()  →  "fish"   // en español o inglés
```

La búsqueda de productos consultaría `%fısh%`, que no existe en la base de datos, y **el usuario no encontraría
nada**. Es un fallo **funcional** que **depende de la configuración de la máquina**: funciona en tu portátil y
falla en producción. Son los fallos más difíciles de depurar, y por eso la herramienta resulta valiosa aquí.

**La corrección sería:**

```java
keyword.toLowerCase(Locale.ROOT)
```

`Locale.ROOT` es el idioma "neutro", el adecuado cuando el texto se usa internamente (una consulta SQL) y no se le
muestra a una persona.

**Detalle extra que suma puntos:** los dos avisos están en el **mismo flujo** y revelan trabajo duplicado. El
controlador ya pasa el texto a minúsculas y se lo entrega al servicio, que **vuelve** a hacerlo. O sea, la misma
conversión se hace dos veces y las dos veces mal.

#### `UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR` — campo sin inicializar y usado

**Dónde:** `Order.getFormattedOrderDate`, línea 678.

```java
public String getFormattedOrderDate() {
  return orderDate.format(ORDER_DATE_FORMATTER);   // orderDate puede ser null
}
```

**El problema.** El campo `orderDate` **no se asigna en el constructor**: solo se rellena en `initOrder(...)`
(línea 614) o con `setOrderDate(...)`. Por tanto:

```java
new Order().getFormattedOrderDate();   // → NullPointerException
```

Es un **fallo real y comprobable en una línea**. Que lleguen o no a darse las condiciones depende de cómo se use
la clase, pero el método es público y no se protege de nada.

**Correcciones posibles:** inicializar `orderDate` en el constructor, devolver cadena vacía o `Optional` cuando sea
`null`, o documentar y validar la precondición.

**Por qué es `UWF_` (y no `NP_`).** El aviso lo genera el detector `UnreadFields`, que estudia **cómo se usan los
campos**, no el detector de nulos. Su razonamiento es: *"este campo nunca se escribe en el constructor y aquí se
usa sin comprobarlo"*. Está en la categoría `STYLE`, pero el impacto es funcional: es una excepción en ejecución.
Es un buen ejemplo para explicar que **la categoría no equivale a la gravedad**.

### 7.3 Los 13 discutibles: `EI_EXPOSE_REP` y `EI_EXPOSE_REP2`

**Qué significan:**

| Patrón | Dirección | Significado |
|---|---|---|
| `EI_EXPOSE_REP` | **Salida** | Un *getter* **devuelve** la referencia a un objeto mutable interno |
| `EI_EXPOSE_REP2` | **Entrada** | Un *setter* o constructor **guarda** la referencia a un objeto mutable que viene de fuera |

**El riesgo, con un ejemplo del proyecto:**

```java
List<CartItem> items = cart.getCartItemList();
items.clear();                  // ¡he vaciado el carrito desde fuera, sin pasar por Cart!
```

`Cart` mantiene además un `Map` interno sincronizado con esa lista, así que una modificación por fuera puede dejar
el objeto en un **estado incoherente**: el mapa diría que hay productos y la lista estaría vacía. El riesgo es
real, y la solución canónica serían **copias defensivas**
(`return Collections.unmodifiableList(itemList)` o `new ArrayList<>(itemList)`).

**Por qué los dejamos y los clasificamos como discutibles, no como falsos positivos:**

1. Son clases **JavaBean** que MyBatis rellena por reflexión y que las **JSP** leen con sus etiquetas. Ese estilo
   (getters y setters que exponen el estado) es **el diseño que la tecnología espera**.
2. Devolver copias en `getCartItemList()` **rompería el comportamiento actual**: `CartController` recorre el
   carrito con un `Iterator` y llama a `cartItems.remove()` para quitar líneas. Si el *getter* devolviese una copia
   o una lista inmutable, eso dejaría de funcionar o lanzaría una excepción.
3. En `AccountSession` el objeto se guarda en la **sesión HTTP** de un solo usuario, por lo que la exposición no
   cruza fronteras entre usuarios.

Conclusión defendible: **es deuda técnica asumida y documentada**, no un error de la herramienta. Ocultarlos en el
filtro sería maquillar el informe; justificarlos es la actitud correcta de ingeniería.

### 7.4 Los 4 falsos positivos excluidos

#### `EI_EXPOSE_REP2` en los constructores de los servicios (3 avisos)

```java
public AccountService(AccountMapper accountMapper) {
  this.accountMapper = accountMapper;   // SpotBugs: "guardas un objeto mutable externo"
}
```

Aquí **compartir la referencia es el objetivo**: es **inyección de dependencias** de Spring. El mapper es un
*singleton* gestionado por el contenedor, y copiarlo sería imposible y absurdo. El patrón está pensado para
estructuras de datos (`Date`, `List`, arrays), no para colaboradores inyectados; aplicado a esto, es un **falso
positivo** claro.

#### `DLS_DEAD_LOCAL_STORE` en `CartController.updateCartQuantities` (1 aviso)

```java
} catch (NumberFormatException e) {      // línea 167
  // ignore invalid numeric input on purpose
}
```

El patrón avisa de valores que se guardan en una variable y nunca se leen. La variable aquí es **la excepción
capturada**, que se ignora **a propósito**: si el usuario escribe algo que no es un número en la cantidad, el
carrito simplemente no cambia. No hay valor muerto que delate un error.

*(Nota: ignorar una excepción sin más podría discutirse como mala práctica; para eso el patrón adecuado sería
`DE_MIGHT_IGNORE`, que no se ha disparado aquí. Nuestra exclusión se limita a este patrón, método y clase, así que
no tapa otros avisos del mismo sitio.)*

---

## 8. Decisiones y su justificación (chuleta de defensa)

| Decisión | Justificación |
|---|---|
| **Activar los 190 detectores** | El objetivo de la práctica es aplicar la herramienta, y con 2 detectores el análisis no es representativo |
| **`threshold=Low`** | Los 3 fallos reales tienen confianza baja: con el umbral heredado no se habrían encontrado |
| **`effort=Max`** | Buscamos profundidad, no velocidad: el proyecto es pequeño (26 clases) y el análisis tarda segundos |
| **No corregir los fallos** | La práctica pide **detectar y analizar**. Si se corrigen, desaparecen del informe y se pierde la evidencia. Además cambiaría el código que están midiendo las otras herramientas (JaCoCo, PiTest, JETM), así que es una decisión de grupo |
| **Excluir solo 4 avisos** | Son los únicos **falsos positivos** demostrables. Los 13 de encapsulación son riesgos reales asumidos por diseño: ocultarlos falsearía el resultado |
| **Filtro XML y no `@SuppressFBWarnings`** | No toca el código de la aplicación y centraliza las justificaciones en un fichero revisable |
| **`<Match>` quirúrgicos** | Clase + método + patrón, en lugar de apagar un patrón en todo el proyecto |
| **`failOnError=false`** | Un build roto por 16 avisos que no se van a corregir bloquearía a todo el grupo |
| **`pluginManagement`** | Análisis bajo demanda, sin ralentizar el build ajeno, y coherente con el CheckStyle del compañero |
| **No analizar los tests** | Se verifica la aplicación; además los tests tienen patrones propios (mocks, asignaciones sin usar) que meterían ruido |
| **No añadir FindSecBugs** | Es una ampliación de seguridad que no pide el enunciado. Queda identificada como mejora |

---

## 9. Preguntas probables del profesor

**¿SpotBugs es estática o dinámica? ¿Por qué?**
Estática: analiza el bytecode compilado sin ejecutar el programa. No hace falta ni arrancar la aplicación ni pasar
los tests.

**¿Y por qué de caja blanca?**
Porque razona sobre la estructura interna: el grafo de flujo de control de cada método y el flujo de datos. No
mira entradas y salidas contra una especificación, que es lo propio de la caja negra (jqwik, por ejemplo).

**¿Funcional o no funcional?**
Funcional: los patrones apuntan a defectos de comportamiento (una excepción, un estado corrompible, una búsqueda
que no encuentra nada). Por contraste, CheckStyle es no funcional, porque mira el estilo.

**Entonces, ¿en qué se diferencia de CheckStyle?**
En qué leen y qué buscan. CheckStyle lee el **fuente** y comprueba **cómo está escrito** el código; SpotBugs lee
el **bytecode** y comprueba **si puede fallar**. Un proyecto puede tener 150 violaciones de CheckStyle y funcionar
perfectamente; un solo aviso `CORRECTNESS` de SpotBugs puede ser un fallo en producción.

**¿Por qué analiza bytecode y no el código fuente?**
Porque así ve lo que realmente se ejecuta, incluido lo que añade el compilador, y no depende de la configuración
del proyecto. El precio es que hay que compilar antes y que no puede opinar de estilo.

**¿Cuántos detectores tiene y cuántos usasteis?**
190 detectores (8 desactivados por defecto) capaces de reportar 507 patrones. Usamos todos: el proyecto venía
limitado a 2.

**¿Qué diferencia hay entre detector y patrón?**
El detector es el analizador que recorre el código; el patrón es el tipo de aviso. Un detector puede reportar
varios patrones: `FindReturnRef` reporta `EI_EXPOSE_REP` y `EI_EXPOSE_REP2`.

**¿Qué es la confianza y qué es el rank?**
La confianza (alta, media, baja) mide lo seguro que está SpotBugs de no equivocarse. El rank (1–20) estima la
gravedad si tiene razón. Aquí todo está en rank 17–20, la banda menos grave, y los 3 fallos reales son de confianza
baja. Son dimensiones independientes.

**¿Por qué solo 16 avisos en todo un proyecto?**
Porque JPetStore es la aplicación de demostración de MyBatis: código pequeño, revisado y mantenido durante años.
Un resultado limpio es un resultado válido, y lo relevante es que incluso así aparecen 3 fallos reales.

**¿Cómo gestionáis los falsos positivos?**
Revisando cada aviso a mano y clasificándolo. Solo 4 son falsos positivos demostrables, y se excluyen en
`config/spotbugs/spotbugs-exclude.xml` con una justificación escrita al lado de cada regla y con coincidencias
limitadas a clase, método y patrón, para no tapar nada más.

**¿Por qué no habéis corregido los fallos?**
Porque la práctica consiste en detectar y analizar, y porque el código es la base de medición de las demás
herramientas del grupo. Está documentado cómo se corregirían: `toLowerCase(Locale.ROOT)` e inicializar `orderDate`.
Si se quiere, se corrige y se vuelve a ejecutar para mostrar un antes y un después: 16 → 13 avisos.

**¿Detectaría SpotBugs un error de lógica de negocio, por ejemplo que el IVA se calcule mal?**
No. Solo reconoce **patrones** que ya conoce; no sabe cuál es el comportamiento esperado. Para eso están las
pruebas: jqwik comprobando propiedades y PiTest midiendo si esas pruebas detectarían un fallo introducido.

**¿Analiza todo el código?**
Todo el código Java de la aplicación: 26 clases y 1.026 métodos, los 5 paquetes. Quedan fuera los tests (opción
`includeTests`, desactivada a propósito) y las JSP, que no son bytecode.

**¿Cómo lo integraríais en integración continua?**
Moviendo el plugin a `<plugins>` con una ejecución del objetivo `check` atada a la fase `verify` y
`failOnError=true`, probablemente con `maxRank` para que solo bloqueen los avisos graves. También se puede generar
el informe en formato SARIF y publicarlo en GitHub Code Scanning, o enviarlo a SonarQube.

**¿Qué limitaciones tiene?**
Falsos positivos y falsos negativos (inevitables: el problema es indecidible), solo conoce sus patrones, no entiende
la lógica de negocio, no cubre JSP ni SQL y los resultados dependen mucho de la configuración, como demostró el
punto de partida de este proyecto.

**Si tuvieseis que ampliarlo, ¿qué haríais?**
Añadir **FindSecBugs** (unos 140 patrones de seguridad: inyección SQL, XSS, *path traversal*), muy pertinente en
una tienda web, y **fb-contrib** para patrones adicionales. Y tests que demuestren los 3 fallos encontrados.

---

## 10. Cómo hacer la demostración en vivo

```bash
# 0) Situarse en la rama
git switch SpotBugs

# 1) Mostrar el punto de partida (configuración heredada)
#    Comentar temporalmente el bloque del pom o enseñar el POM padre:
#    ~/.m2/repository/org/mybatis/mybatis-parent/52/mybatis-parent-52.pom  (líneas 431-445)

# 2) El análisis completo, con los avisos en consola
./mvnw compile spotbugs:check

# 3) La interfaz gráfica: navegar por patrón, categoría, clase, ver la línea y la explicación oficial
./mvnw compile spotbugs:gui

# 4) El informe XML
#    target/spotbugs-reports/spotbugsXml.xml
```

Qué conviene enseñar, en este orden:

1. **El POM padre**, para contar el punto de partida: 2 detectores y umbral alto.
2. **Las 12 líneas del `pom.xml`**, explicando `combine.self="override"`.
3. **La salida de `spotbugs:check`**: 16 avisos.
4. **La GUI**, filtrando por categoría, para mostrar el reparto y abrir los 3 fallos reales.
5. **El filtro de exclusiones**, para explicar la gestión de falsos positivos.
6. **Los 3 fallos reales en el código**, explicando el caso del turco y el `NullPointerException` en una línea.

Para demostrar que un aviso es real sin hacer la corrección, basta una línea en una consola `jshell`:

```java
java.util.Locale.setDefault(new java.util.Locale("tr"));
"FISH".toLowerCase();   // → "fısh"
```

---

## 11. Posibles ampliaciones

| Ampliación | Qué aportaría | Coste |
|---|---|---|
| Tests que demuestren los 3 fallos | Pasar de "SpotBugs dice que hay un fallo" a "lo hemos reproducido" | ~30 min |
| Corregir los fallos y volver a ejecutar | Informe de antes y después: 16 → 13 avisos | ~30 min |
| **FindSecBugs** | ~140 patrones de seguridad; muy pertinente en una tienda web | ~30 min |
| **fb-contrib** | Patrones adicionales de calidad y rendimiento | ~20 min |
| `includeTests=true` | Analizar también el código de pruebas | ~10 min |
| Puerta de calidad en CI | `check` en `verify` con `failOnError=true` y `maxRank` | ~20 min |
| Informe SARIF + GitHub Code Scanning | Los avisos aparecerían anotados en los *pull requests* | ~30 min |

---

## 12. Glosario

| Término | Significado |
|---|---|
| **Bytecode** | El código que genera el compilador de Java (ficheros `.class`) y que ejecuta la JVM |
| **Bug pattern** (patrón de error) | Forma de escribir código que suele indicar un fallo. SpotBugs 4.9.8 conoce 507 |
| **Detector / visitor** | El analizador que recorre el código buscando uno o varios patrones. Hay 190 |
| **Confianza** (*confidence* / *priority*) | Lo seguro que está la herramienta de que el aviso es correcto: alta, media o baja |
| **Rank** | Gravedad estimada, de 1 (peor) a 20. Bandas: *scariest*, *scary*, *troubling*, *of concern* |
| **Categoría** | Familia del patrón: `CORRECTNESS`, `MALICIOUS_CODE`, `I18N`, `STYLE`… |
| **Falso positivo** | Aviso que, revisado, no es un fallo |
| **Falso negativo** | Fallo que existe y la herramienta no detecta |
| **Flujo de control / flujo de datos** | Las dos técnicas de análisis: por dónde puede pasar la ejecución y qué valores puede tener cada variable |
| **ASM** | Librería que SpotBugs usa para leer el bytecode |
| **`effort`** | Profundidad del análisis: `Min`, `Default`, `Max` |
| **`threshold`** | Umbral de confianza para mostrar un aviso |
| **`pluginManagement`** | Sección de Maven donde se configura un plugin sin que se ejecute automáticamente |
| **`combine.self="override"`** | Atributo de Maven que sustituye la configuración heredada del POM padre en lugar de fusionarla |
| **Copia defensiva** | Devolver o guardar una copia en lugar de la referencia original, para proteger el estado interno |
| **`Locale.ROOT`** | Idioma neutro, el adecuado para conversiones internas de texto |
