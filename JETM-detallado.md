# JETM en JPetStore 6 — guía completa

Documento de apoyo para entender y defender la implementación de JETM en este proyecto.
Complementa a `JETM.md`, que es el resumen corto de la rama `JETM`.

---

## 1. Qué es JETM

**JETM** son las siglas de **Java Execution Time Measurement Library**. Es una librería que **mide cuánto tarda en
ejecutarse un trozo de código**: tú marcas dónde empieza y dónde acaba, y JETM va acumulando los tiempos y te da
estadísticas.

Un resultado real de este proyecto:

```
|---------------------------------------------------|------|---------|-------|-------|---------|
|                 Measurement Point                 |   #  | Average |  Min  |  Max  |  Total  |
|---------------------------------------------------|------|---------|-------|-------|---------|
| order.insertOrder(3 lines)                        | 1000 |   0.663 | 0.241 | 6.827 | 663.044 |
|   OrderService.insertOrder                        | 1000 |   0.646 | 0.236 | 6.194 | 646.217 |
|     SequenceMapper.getSequence                    | 1000 |   0.100 | 0.030 | 5.333 | 100.177 |
|     ItemMapper.updateInventoryQuantity            | 3000 |   0.034 | 0.008 | 3.459 | 101.221 |
|     ...                                           |      |         |       |       |         |
```

Se lee así: *guardar un pedido de 3 líneas se ejecutó 1.000 veces, tardó de media 0,663 ms, la más rápida 0,241 ms
y la más lenta 6,827 ms; dentro de cada ejecución, leer la secuencia tardó 0,100 ms y la actualización del stock se
ejecutó 3 veces por pedido.*

### La idea clave: es un cronómetro, no un analizador

Esta distinción es **lo más importante para la defensa**, porque marca la diferencia con todas las demás
herramientas del grupo:

| | SpotBugs, CheckStyle | **JETM** |
|---|---|---|
| ¿Qué hace? | **Revisa** el código solo | **Mide** lo que tú le pidas |
| ¿Hay que programar? | No | **Sí**: hay que escribir las mediciones |
| ¿Cubre todo el código automáticamente? | Sí | **No**: solo aparece lo que se ejecuta y se marca |
| Resultado | Lista de avisos | Tabla de tiempos |

JETM **no dice si algo está bien o mal**. Dice *"esto tardó 0,66 ms"*. Convertir eso en una **prueba** (algo que
pase o falle) es trabajo nuestro: hay que fijar un **límite** y comprobarlo. Es un punto que conviene decir en voz
alta en la defensa, porque demuestra que entiendes qué aporta la herramienta y qué tienes que aportar tú.

### De dónde surge

- Proyecto **`fm.void.jetm`**, publicado en `http://jetm.void.fm`, alojado históricamente en **SourceForge**.
- Licencia **BSD**.
- Nace a mediados de los 2000 para cubrir un hueco muy concreto: medir **rendimiento dentro de una aplicación real
  en ejecución** (lo que hoy se llama *application performance monitoring* ligero), sin el peso ni la complejidad
  de un *profiler* comercial.
- La última versión es la **1.2.3**, de alrededor de **2010–2012**. El proyecto está **abandonado**, aunque sigue
  publicado en Maven Central y **funciona con Java 21**, como hemos comprobado.
- Se distribuye en dos artefactos:
  - **`jetm`** (núcleo): monitores, temporizadores, agregadores y el *renderer* de texto.
  - **`jetm-optional`**: integraciones (Spring, AOP, web, JMX), consola HTTP, informes HTML y gráficas.

**Ninguno de los dos arrastra dependencias**, lo cual es una ventaja real al integrarlo en un proyecto ajeno: no
hay riesgo de conflicto de versiones con Spring, MyBatis o JUnit.

### Por qué una herramienta abandonada

Es una pregunta que puede caer, así que conviene tener la respuesta preparada:

1. **La pide el enunciado de la práctica**, que la clasifica como herramienta dinámica, de caja blanca y centrada
   en aspectos no funcionales.
2. **Sigue siendo didácticamente válida**: su modelo (punto de medición, agregación, *renderer*) es el mismo que
   usan herramientas modernas como Micrometer, Dropwizard Metrics o los *spans* de OpenTelemetry. Quien entiende
   JETM entiende el patrón.
3. **Funciona**: se integra con Maven y con Spring sin tocar nada del código de la aplicación.
4. Pero hay que **decir también sus límites** y qué se usaría hoy: **JMH** para micro-*benchmarks* rigurosos,
   **Micrometer + Prometheus/Grafana** para producción, **JMeter o Gatling** para pruebas de carga, y
   **async-profiler o JFR** para perfilado.

### Clasificación para la asignatura

| Eje | JETM | Por qué |
|---|---|---|
| Estática / **dinámica** | **Dinámica** | Hay que **ejecutar** el programa: sin ejecución no hay tiempos |
| **Caja blanca** / negra | **Caja blanca** | Los puntos de medición se colocan **dentro** del código, sabiendo su estructura: se mide un servicio concreto y, dentro, cada consulta que hace |
| Funcional / **no funcional** | **No funcional** | No comprueba *qué* hace el programa, sino *cómo de rápido* lo hace: rendimiento y escalabilidad |

**Matiz que da puntos**: medir tiempos *podría* hacerse en caja negra (cronometrar peticiones HTTP desde fuera, como
JMeter). Lo que hace a **nuestro** uso de caja blanca es que medimos **por dentro**: no solo cuánto tarda guardar un
pedido, sino **en qué consultas SQL concretas se va ese tiempo**. Esa es la razón de usar un monitor anidado.

---

## 2. Cómo funciona por dentro

### 2.1 Los cinco conceptos

```
   Tu código
      │  createPoint("nombre")  ─────────┐
      │                                  │
      ▼                                  ▼
  [ EtmPoint ]  ──collect()──►  [ EtmMonitor ] ──►  [ Aggregator ] ──►  [ Renderer ]
   mide una                      recoge y            acumula y            muestra la
   ejecución                     coordina            calcula medias       tabla
                                     │
                                     ▼
                                [ ExecutionTimer ]
                                 el reloj (nanosegundos)
```

| Concepto | Qué es | Clases en el proyecto |
|---|---|---|
| **`EtmPoint`** | Una **medición individual**. Se crea al entrar en el código a medir y se cierra con `collect()` | Una por cada una de las 1.000 repeticiones |
| **`EtmMonitor`** | El **cronómetro central**. Crea los puntos, los recoge y coordina todo | `NestedMonitor` (servicios), `FlatMonitor` (carrito) |
| **`ExecutionTimer`** | El **reloj** que usa el monitor | `Java15NanoTimer` |
| **`Aggregator`** | Acumula las mediciones y calcula nº de ejecuciones, media, mínimo, máximo y total | `RootAggregator` |
| **`MeasurementRenderer`** | Dibuja o exporta el resultado | `SimpleTextRenderer` |

### 2.2 Qué ocurre exactamente en una medición

```java
EtmPoint point = monitor.createPoint("order.insertOrder(3 lines)");
try {
  orderService.insertOrder(order);
} finally {
  point.collect();
}
```

1. `createPoint` pide al **temporizador** la marca de tiempo actual y la guarda en el punto. Si ya hay otro punto
   abierto en el mismo hilo y el monitor es anidado, apunta cuál es su **padre**.
2. Se ejecuta el código a medir.
3. `collect()` pide otra marca de tiempo, calcula la diferencia, la convierte a milisegundos y entrega el punto al
   **agregador**.
4. El agregador busca la entrada con ese nombre (y ese padre, si es anidado) y actualiza sus estadísticas:
   `+1` ejecución, recalcula la media, y compara con el mínimo y el máximo.

Detalles importantes:

- **El `try/finally` no es decorativo**: si la operación lanza una excepción, `collect()` se ejecuta igual y la
  medición no se pierde ni deja el punto abierto, lo que falsearía las mediciones anidadas.
- **Las estadísticas se guardan agregadas, no una a una.** JETM no almacena 1.000 tiempos: mantiene contador, suma,
  mínimo y máximo. Por eso es muy ligero en memoria, pero **no puede darte la mediana ni los percentiles** (el
  famoso p95), que es justo lo que pediría un análisis de rendimiento moderno. Es una limitación de diseño que
  conviene mencionar.
- **La propia medición cuesta tiempo** (del orden de nanosegundos). Para operaciones que tardan microsegundos, ese
  coste ya se nota: por eso en el informe hay valores de `< 0.001` que no son fiables al detalle.

### 2.3 Los temporizadores

| Clase | Qué usa | Precisión |
|---|---|---|
| `Java15NanoTimer` | `System.nanoTime()` | Nanosegundos. **Es el que usamos** |
| `SunHighResTimer` | API interna `sun.misc.Perf` | Alta, pero depende de clases internas del JDK antiguas |
| `DefaultTimer` | Elige automáticamente el mejor disponible | Variable |

Elegimos `Java15NanoTimer` **explícitamente** por dos razones defendibles:

1. `System.nanoTime()` es el reloj **monótono** de la JVM, el correcto para medir intervalos. `currentTimeMillis()`
   no sirve: mide la hora del sistema, puede saltar hacia atrás con un ajuste horario y su resolución real en
   Windows puede ser de ~15 ms, que es **20 veces más** que lo que queremos medir.
2. Dejarlo explícito evita depender de lo que `DefaultTimer` decida en cada máquina, es decir, hace la medición
   **reproducible**.

### 2.4 Monitores: plano y anidado

| Monitor | Qué hace | Dónde lo usamos |
|---|---|---|
| `FlatMonitor` | Lista plana: cada nombre, una fila | `CartPerformanceTest`, donde no hay jerarquía |
| `NestedMonitor` | Mantiene una **pila por hilo**: cada punto abierto dentro de otro queda como **hijo** | `ServicePerformanceTest` |
| `NullMonitor` | No mide nada; sirve para desactivar la medición sin tocar el código | — |

El anidamiento es lo que produce este resultado, que es el corazón del análisis:

```
| order.getOrder(3 lines)                  | 1000 |   0.493 |   ← el escenario
|   OrderService.getOrder                  | 1000 |   0.398 |   ← el servicio
|     OrderMapper.getOrder                 | 1000 |   0.117 |   ← 1 consulta
|     LineItemMapper.getLineItemsByOrderId | 1000 |   0.065 |   ← 1 consulta
|     ItemMapper.getItem                   | 3000 |   0.036 |   ← ¡3 consultas por pedido!
|     ItemMapper.getInventoryQuantity      | 3000 |   0.010 |   ← ¡otras 3!
```

Sin anidamiento solo sabrías que tarda 0,493 ms. Con anidamiento **ves por qué** y descubres el problema **N+1**.

### 2.5 Agregadores

| Agregador | Comportamiento |
|---|---|
| `RootAggregator` | Agrega **al momento**, sin búfer. **Es el que usamos** |
| `BufferedTimedAggregator` | Guarda en un búfer y agrega cada X milisegundos |
| `BufferedThresholdAggregator` | Agrega cuando el búfer llega a N mediciones |
| `NotifyingAggregator` | Avisa a un oyente cuando se supera un umbral |
| `FlatAggregator` / `NestedAggregator` | Variantes de agregación plana o jerárquica |

En producción usarías uno **con búfer**, para que medir afecte lo menos posible al rendimiento de la aplicación.
Nosotros usamos `RootAggregator` **a propósito**: los tests necesitan leer las estadísticas **inmediatamente**
después de medir para comprobar los límites, y con un búfer los datos podrían no estar disponibles todavía. Es una
decisión justificable y la pregunta "¿por qué ese agregador?" es muy probable.

### 2.6 Qué más trae la librería (y no usamos)

Conocerlo demuestra que te has leído la herramienta, no solo el ejemplo:

| Paquete | Qué ofrece |
|---|---|
| `etm.contrib.console` | Una **consola HTTP**: levanta un servidor y ves las mediciones en el navegador, en vivo |
| `etm.contrib.renderer.SimpleHtmlRenderer` | Exporta la tabla a **HTML** |
| `etm.contrib.rrd` | Gráficas de evolución temporal con RRD4J |
| `etm.core.jmx` | Exponer las mediciones por **JMX**, para verlas con JConsole |
| `etm.contrib.aggregation.log` | Volcar las mediciones al log |
| `etm.contrib.aop.*` | Integración con AOP: **aopalliance/Spring**, AspectWerkz, JBoss |
| `etm.contrib.integration.spring` | Espacio de nombres `<jetm:...>` para configurarlo en el XML de Spring |
| `etm.contrib.integration.web` | Un filtro de servlets para medir peticiones HTTP completas |
| `etm.core.configuration.XmlEtmConfigurator` | Configuración por XML en vez de por código |

---

## 3. Cómo se activa (y cómo se haría desde cero)

### 3.1 Lo mínimo: tres pasos

**Paso 1 — Dependencia** (no arrastra nada más):

```xml
<dependency>
  <groupId>fm.void.jetm</groupId>
  <artifactId>jetm</artifactId>
  <version>1.2.3</version>
</dependency>
```

**Paso 2 — Arrancar el monitor**, normalmente una sola vez al inicio de la aplicación:

```java
BasicEtmConfigurator.configure();                      // configuración por defecto
EtmMonitor monitor = EtmManager.getEtmMonitor();       // acceso global (patrón singleton)
monitor.start();
```

`BasicEtmConfigurator` tiene variantes: `configure(true)` activa el modo **anidado**, y se le pueden pasar el
temporizador y el agregador que quieras. `EtmManager` es un **registro global**, que es cómodo en una aplicación
pero **poco recomendable en tests**, porque el estado se comparte entre clases de prueba. Por eso nosotros **no lo
usamos**: creamos el monitor a mano, como se explica en el apartado 5.

**Paso 3 — Medir y mostrar**:

```java
EtmPoint point = monitor.createPoint("buscar productos");
try {
  catalogService.searchProductList("fish");
} finally {
  point.collect();
}

monitor.render(new SimpleTextRenderer());
monitor.stop();
```

### 3.2 La forma elegante: sin tocar el código de la aplicación

Meter `createPoint`/`collect` a mano dentro de los servicios sería **contaminar el código de producción con código
de medición**. JETM ofrece dos alternativas, y las dos se basan en **programación orientada a aspectos** (AOP):

**Opción A — Interceptor de Spring (la que usamos):**

```java
MethodInterceptor interceptor = new EtmMethodCallInterceptor(monitor);

BeanNameAutoProxyCreator creator = new BeanNameAutoProxyCreator();
creator.setBeanNames("*Service", "*Mapper");      // a qué beans se aplica
creator.setInterceptorNames("etmInterceptor");
```

Spring envuelve cada servicio y cada mapper en un **proxy**: un objeto que tiene la misma interfaz, recibe las
llamadas, abre un punto de medición, delega en el objeto real y cierra el punto. El servicio **no sabe que lo están
midiendo**.

**Opción B — XML de Spring**, con el espacio de nombres que trae `jetm-optional`:

```xml
<jetm:runtime id="etmMonitor" />
<jetm:monitoring />
```

**Para medir peticiones HTTP completas** existe además un filtro de servlets en
`etm.contrib.integration.web`, que se declara en el `web.xml`.

### 3.3 Cómo se haría de cero en **este** proyecto

Si JPetStore no tuviese nada, el camino completo sería:

1. Añadir las dos dependencias (`jetm` y `jetm-optional`) con `<scope>test</scope>`, porque la medición es parte de
   las pruebas y **no debe acabar en el `.war` que se despliega**.
2. Crear un paquete de pruebas de rendimiento separado: `src/test/java/.../performance/`.
3. Montar un contexto de Spring de prueba que levante los servicios reales sobre la base de datos **HSQLDB en
   memoria** (JPetStore ya tiene una para los tests de los mappers: `MapperTestContext`).
4. Enganchar JETM a ese contexto con el interceptor AOP.
5. Escribir los escenarios: calentar, medir N veces, comprobar límites.
6. Separar estos tests del build normal, porque tardan y no deben romper la compilación de nadie.
7. Volcar la tabla a consola y a fichero para poder documentarla.

Los puntos 5, 6 y 7 son exactamente los que diferencian "usar la librería" de "hacer una prueba de rendimiento
seria", y son los que explicamos en el apartado 5.

---

## 4. La teoría que hay que dominar: por qué medir en Java es difícil

Esta sección es la que distingue una práctica de notable de una de sobresaliente. Un profesor puede preguntar:
*"¿y cómo sé que ese 0,66 ms es verdad?"*.

### 4.1 El calentamiento (JIT)

La JVM **no ejecuta siempre el mismo código máquina**. Al principio interpreta el *bytecode*; cuando detecta que un
método se usa mucho, el compilador **JIT** lo compila a código nativo y lo optimiza (alineado de métodos, bucles,
eliminación de comprobaciones). Resultado: **las primeras ejecuciones son mucho más lentas** que las siguientes.

Lo comprobamos en este proyecto: el primer test que se ejecutaba daba **0,092 ms** de media; con calentamiento
global previo, **0,029 ms**. Tres veces menos. Si no calientas, lo que mides no es el rendimiento del código, sino
el estado del compilador.

**Solución aplicada**: ejecutar la operación muchas veces **sin medir** antes de empezar a medir (200 repeticiones
por operación, más 1.000 rondas con todas las operaciones).

### 4.2 Eliminación de código muerto

Si el JIT ve que calculas un valor y **no lo usas**, puede **eliminar el cálculo entero**. Entonces medirías cero.

```java
cart.getSubTotal();   // ⚠️ el resultado se tira: el JIT podría borrar la llamada
```

**Solución aplicada**: guardar el resultado en un campo `volatile`, que obliga a la JVM a escribirlo de verdad en
memoria y no puede eliminarse.

### 4.3 El ruido

El recolector de basura, el sistema operativo, otros procesos y la propia compilación JIT provocan **picos**. En
nuestras tablas se ve claramente: `updateAccount` tiene una media de 0,42 ms y un **máximo de 28,7 ms**, casi 70
veces más.

**Solución aplicada**: repetir 1.000 veces y razonar sobre la media, el mínimo y el máximo juntos. El mínimo
representa "el código cuando nada le molesta" y el máximo delata los picos.

### 4.4 Un tiempo no es una prueba

"Tarda 0,66 ms" no es ni bueno ni malo: no pasa ni falla. Para convertirlo en **prueba automática** hay que fijar
un **umbral**. Y el umbral tiene un problema conocido: si lo pones ajustado, el test **falla aleatoriamente** en
otro ordenador o cuando la máquina está cargada (un test *flaky*, que es peor que no tener test).

**Solución aplicada**: umbrales de **más de 10 veces** el tiempo medido. No detectan una degradación del 20 %, pero
**sí detectan una catástrofe** —alguien que mete una consulta dentro de un bucle, o que convierte una operación
lineal en cuadrática—, y **nunca fallan por ruido**. Es un compromiso consciente y hay que saber defenderlo así.

### 4.5 Por qué JETM no es JMH

| | JETM | JMH (*Java Microbenchmark Harness*) |
|---|---|---|
| Calentamiento | **Lo haces tú** | Automático y configurable |
| Aislamiento | Mismo proceso | **JVM nueva** por *benchmark* (*forking*) |
| Código muerto | Lo evitas tú (`volatile`) | `Blackhole`, pensado para eso |
| Estadísticas | Media, mínimo, máximo | Percentiles, desviación, intervalos de confianza |
| Pensado para | Medir **dentro de una aplicación** | Medir **fragmentos de código aislados** |

Conclusión defendible: **JETM mide operaciones de negocio completas dentro de su contexto real** (con Spring,
MyBatis y base de datos), que es justo lo que queremos aquí. Para comparar dos implementaciones de un algoritmo,
JMH sería la herramienta correcta.

---

## 5. Qué hicimos, paso a paso

### Paso 1 — Rama propia

```bash
git switch -c JETM master
```

### Paso 2 — Comprobar que la librería existe y es usable

Primera sorpresa: en una conversación anterior dimos por hecho que JETM **no estaba bien publicado** en Maven
Central. Al comprobarlo, **sí estaba** (`fm.void.jetm:jetm:1.2.3`) y además **sin dependencias**, lo que simplificó
mucho la integración. Verificarlo antes de diseñar nada ahorró trabajo.

### Paso 3 — Dependencias en el `pom.xml`

```xml
<!-- JETM: medición de tiempos de ejecución (tests de rendimiento, perfil jetm) -->
<dependency>
  <groupId>fm.void.jetm</groupId>
  <artifactId>jetm</artifactId>
  <version>1.2.3</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>fm.void.jetm</groupId>
  <artifactId>jetm-optional</artifactId>
  <version>1.2.3</version>
  <scope>test</scope>
</dependency>
```

**Por qué `test`**: JPetStore se empaqueta como `.war` para desplegar. Con *scope* `test`, JETM se usa al ejecutar
las pruebas pero **no se incluye en el artefacto desplegable**. Si midiésemos en producción, el *scope* sería
`compile` y habría que valorar la sobrecarga.

**Por qué también `jetm-optional`**: es el que contiene `EtmMethodCallInterceptor`, la integración con AOP que
permite medir sin tocar el código de la aplicación.

### Paso 4 — Separar los tests de rendimiento del build normal

Dos bloques más en el `pom.xml`:

```xml
<!-- En build > plugins: los tests de rendimiento NO se ejecutan en el build normal -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <excludedGroups>performance</excludedGroups>
  </configuration>
</plugin>
```

```xml
<!-- Perfil jetm: ejecuta SOLO los tests de rendimiento -->
<profile>
  <id>jetm</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <groups>performance</groups>
          <excludedGroups combine.self="override" />
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```

Cómo funciona:

- Cada clase de rendimiento lleva la anotación **`@Tag("performance")`** de JUnit 5.
- En el build normal, `excludedGroups` descarta esa etiqueta: `./mvnw test` ejecuta **los 98 tests de siempre**.
- Con `-P jetm`, `groups` selecciona **solo** esa etiqueta, y `excludedGroups combine.self="override"` **anula** la
  exclusión heredada de la configuración general (si no, se excluiría y se incluiría a la vez, y no se ejecutaría
  nada). Es el mismo mecanismo de Maven que usamos en SpotBugs para vaciar la lista de detectores del POM padre.

**Por qué separarlos**:

1. **Tardan**: ~10 segundos frente a los milisegundos de un test normal, porque repiten cada operación 1.200 veces.
2. **No deben romper el build de nadie**: un test de tiempos puede fallar en un ordenador lento o cargado. Que eso
   bloquee la compilación de un compañero sería inaceptable.
3. **Miden, no verifican**: su sitio natural es una ejecución aparte, igual que haría un equipo real con sus
   pruebas de rendimiento nocturnas.

El compañero que hizo jqwik usó la misma idea (un perfil aparte), aunque con otra técnica: él separó los tests en
**otra carpeta**; nosotros usamos **etiquetas** de JUnit 5, que es el mecanismo estándar y no obliga a tocar la
estructura del proyecto.

### Paso 5 — El contexto de Spring con JETM enganchado

Es la pieza central. Dentro de `ServicePerformanceTest`:

```java
@Configuration
@EnableTransactionManagement
@Import({ MapperTestContext.class, CatalogService.class, AccountService.class, OrderService.class })
static class Context {

  @Bean(initMethod = "start", destroyMethod = "stop")
  EtmMonitor etmMonitor() {
    return new NestedMonitor(new Java15NanoTimer(), new RootAggregator());
  }

  @Bean
  MethodInterceptor etmInterceptor(EtmMonitor etmMonitor) {
    return new EtmMethodCallInterceptor(etmMonitor) {
      @Override
      protected String calculateName(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
      }
    };
  }

  @Bean
  static BeanNameAutoProxyCreator etmAutoProxyCreator() {
    BeanNameAutoProxyCreator creator = new BeanNameAutoProxyCreator();
    creator.setBeanNames("*Service", "*Mapper");
    creator.setInterceptorNames("etmInterceptor");
    creator.setProxyTargetClass(true);
    return creator;
  }
}
```

Decisión por decisión:

| Decisión | Por qué |
|---|---|
| `@Import(MapperTestContext.class)` | **Reutilizamos** la base de datos HSQLDB en memoria que ya usaban los tests de los mappers, con el esquema y los datos de ejemplo. Ni montamos nada nuevo ni hace falta instalar una base de datos |
| `@EnableTransactionManagement` | `OrderService.insertOrder` lleva `@Transactional`. Sin esto, mediríamos la operación **sin transacción**, que es un escenario distinto del real |
| `NestedMonitor` | Para ver las consultas SQL **dentro** de cada servicio: es lo que convierte la medición en caja blanca |
| `Java15NanoTimer` | Reloj monótono de nanosegundos, explícito y reproducible (apartado 2.3) |
| `RootAggregator` | Sin búfer: los tests leen las estadísticas justo después de medir (apartado 2.5) |
| `initMethod/destroyMethod` | Spring arranca y para el monitor con el ciclo de vida del contexto: no hay que acordarse de hacerlo a mano |
| `*Service`, `*Mapper` | Se mide **toda** la capa de servicios y **todos** los mappers, sin enumerarlos uno a uno |
| **Monitor como `@Bean`, no `EtmManager`** | `EtmManager` es un registro **global**: dos clases de test compartirían el monitor y las mediciones se mezclarían. Como *bean*, cada contexto tiene el suyo |

### 5.1 Los dos problemas técnicos que hubo que resolver

Esto es material de oro para la defensa: demuestra comprensión real, no copia de un tutorial.

**Problema 1: `BeanNotOfRequiredTypeException`**

Primer intento, el contexto ni arrancaba:

```
Bean named 'org.mybatis.jpetstore.service.AccountService' is expected to be of type
'AccountService' but was actually of type 'jdk.proxy2.$Proxy61'
```

**Causa.** Spring puede crear dos tipos de proxy:

- **Proxy dinámico de JDK**: implementa las **interfaces** del objeto. Si la clase no tiene interfaz, el proxy
  **no es compatible** con el tipo original.
- **Proxy de clase (CGLIB)**: genera una **subclase** del objeto, así que sí es compatible.

`AccountService` es una clase sin interfaz, así que al envolverla con un proxy de JDK dejaba de ser un
`AccountService` y la inyección fallaba.

**Solución.** `creator.setProxyTargetClass(true)`: fuerza el proxy por subclase. Además, los servicios ya estaban
envueltos por el proxy de `@Transactional`, así que el de JETM tenía que envolver **ese** proxy de la misma forma.

**Problema 2: los mappers se llamaban `$Proxy61`**

Los mappers de MyBatis **son interfaces sin implementación**: MyBatis genera la implementación en tiempo de
ejecución. Con el nombre que pone JETM por defecto, la tabla habría salido así:

```
| $Proxy61.getOrder | 1000 | ...
```

Ilegible, y además con números que cambian en cada ejecución.

**Solución.** Sobrescribir `calculateName` para usar `method.getDeclaringClass()`, que es la **interfaz declarada**
(`OrderMapper`), en vez de la clase real del proxy. De ahí salen nombres como `OrderMapper.getOrder`.

### Paso 6 — La metodología de medición (`JetmSupport`)

Clase de apoyo con lo común a todos los escenarios:

```java
static void measure(EtmMonitor monitor, String pointName, Runnable operation, Runnable restore) {
  // 1. Calentamiento SIN medir
  monitor.disableCollection();
  try {
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {     // 200
      operation.run();
      if (restore != null) restore.run();
    }
  } finally {
    monitor.enableCollection();
  }

  // 2. Medición
  for (int i = 0; i < MEASURED_ITERATIONS; i++) {     // 1000
    EtmPoint point = monitor.createPoint(pointName);
    try {
      operation.run();
    } finally {
      point.collect();
    }
    if (restore != null) restore.run();               // restaurar FUERA de la medición
  }
}
```

Detalles que conviene saber explicar:

- **`disableCollection()`** no para el monitor: lo deja funcionando pero **descarta** lo que se mide. Es
  fundamental, porque como el interceptor AOP está activo, sin esto el calentamiento contaminaría las mediciones
  anidadas de los servicios y los mappers.
- **El parámetro `restore`** permite medir operaciones que **cambian el estado**. Por ejemplo, para medir "quitar un
  producto del carrito" hay que volver a añadirlo entre medición y medición, pero **fuera** del punto de medición,
  para no contaminar el tiempo.
- **`aggregate(...)`** lee el resultado navegando por el camino de nombres (escenario → servicio → mapper), lo que
  permite comprobar en el test **cuántas consultas** se hicieron, no solo cuánto tardaron.
- **`writeReport(...)`** vuelca la tabla a consola y a `target/jetm/*.txt`, para poder documentar los resultados.

### Paso 7 — Los escenarios

**`ServicePerformanceTest`** — 18 escenarios que cubren **los 16 métodos** de los 3 servicios, más `Order.initOrder`.
Cada test tiene esta forma:

```java
@Test
void insertOrder() {
  String scenario = "order.insertOrder(3 lines)";
  JetmSupport.measure(monitor, scenario, () -> orderService.insertOrder(newOrder()));

  assertAverageBelow(scenario, MAX_AVERAGE_WRITE_MS);
  assertQueriesPerCall(1, scenario, "OrderService.insertOrder", "OrderMapper.insertOrder");
  assertQueriesPerCall(3, scenario, "OrderService.insertOrder", "ItemMapper.updateInventoryQuantity");
  assertQueriesPerCall(3, scenario, "OrderService.insertOrder", "LineItemMapper.insertLineItem");
}
```

Fíjate en que **hay dos tipos de comprobación**:

1. **De tiempo**: la media está por debajo del límite.
2. **De número de consultas**: la operación hace exactamente las sentencias SQL esperadas.

La segunda es más valiosa de lo que parece y conviene destacarla en la defensa: **es estable** (no depende del
ordenador) y **detecta el problema N+1**. Si alguien añade una consulta dentro de un bucle, el test falla aunque la
máquina sea rapidísima. Es convertir una medición de rendimiento en una **prueba de regresión fiable**.

**`CartPerformanceTest`** — mide cómo **escala** el carrito, usando JETM de forma programática:

- `getSubTotal()` con 10, 100, 1.000 y 10.000 líneas → comprueba que el crecimiento es **lineal**.
- Las otras cinco operaciones del carrito sobre un carrito de 1.000 líneas.

La comprobación de escalabilidad es distinta de un umbral absoluto:

```java
assertThat(average10000 / average1000).isLessThan(MAX_GROWTH_FACTOR);   // 30
```

Con 10 veces más datos, un algoritmo **lineal, O(n)**, tarda unas 10 veces más; uno **cuadrático, O(n²)**, unas
100. El límite de 30 distingue claramente entre los dos casos y **no depende de lo rápido que sea el ordenador**,
porque es una **proporción entre dos medidas tomadas en la misma máquina**. Es, probablemente, la comprobación
mejor diseñada de todo el conjunto.

### Paso 7 bis — Los escenarios web (controladores)

Los 5 controladores se miden **llamándolos directamente desde el test**, con las piezas simuladas que trae
`spring-test`:

| Pieza real | Pieza simulada |
|---|---|
| `HttpSession` del navegador | `MockHttpSession` |
| `HttpServletRequest` | `MockHttpServletRequest` |
| `Model` de Spring MVC | `ExtendedModelMap` |

Los controladores se añaden al contexto y el mismo interceptor de JETM los envuelve (`*Controller` en
`JetmProxyConfig`), así que la tabla pasa a tener **cuatro niveles**: acción → controlador → servicio → consulta.

```
| GET /order/view?orderId                    | 1000 |   1.127 |   ← la acción del usuario
|   OrderController.viewOrder                | 1000 |   1.063 |   ← el controlador
|     OrderService.getOrder                  | 1000 |   1.053 |   ← el servicio
|       OrderMapper.getOrder                 | 1000 |   0.282 |   ← las consultas
|       LineItemMapper.getLineItemsByOrderId | 1000 |   0.175 |
|       ItemMapper.getItem                   | 3000 |   0.098 |   ← el N+1, visible de punta a punta
|       ItemMapper.getInventoryQuantity      | 3000 |   0.029 |
```

**Problemas que hubo que resolver, y cómo:**

| Problema | Solución |
|---|---|
| Muchas acciones exigen un usuario identificado | Se prepara una `MockHttpSession` con el `AccountSession` y un carrito, igual que haría el login |
| `GET /order/list` daría tiempos crecientes, porque los escenarios de compra van creando pedidos | Una **segunda sesión** con un usuario propio (`perf-web-read`) y exactamente 10 pedidos, que nunca se usa para comprar |
| Confirmar un pedido **vacía el carrito** y consume el pedido de la sesión | Una restauración entre medidas que repone carrito y pedido, **fuera** de la medición |
| `signoff` invalida la sesión | Se mide sobre una sesión nueva en cada repetición |
| La restauración llamaba a código instrumentado y ensuciaba el informe con filas sueltas | `JetmSupport` **desactiva la recogida** (`disableCollection`) mientras restaura |

Ese último punto es un buen ejemplo de lo que cuesta medir bien: la primera versión del informe tenía una fila
`CatalogService.getItem` con **6.000 llamadas** que no correspondía a ningún escenario; eran las llamadas que hacía
la restauración del carrito. Detectarlo y silenciarlo es parte del trabajo de hacer una medición limpia.

### Paso 8 — Preparación de datos y trampas evitadas

En `@BeforeAll` se preparan los datos **sin medir** y se calienta todo. Tres decisiones con su motivo:

| Problema | Solución |
|---|---|
| `insertAccount` necesita un usuario distinto cada vez (la clave primaria no se puede repetir) | Un contador: `perf-0`, `perf-1`, … |
| `getOrdersByUsername` daría tiempos crecientes, porque los tests de `insertOrder` van creando miles de pedidos | Se crean **10 pedidos para un usuario propio** (`perf-orders`) y se consulta ese: medida **estable y reproducible**, independiente del orden de ejecución de los tests |
| El primer test salía 3 veces más lento | 1.000 rondas de calentamiento **global** con todas las operaciones, para calentar también el código común de MyBatis, JDBC y HSQLDB |

Ese segundo punto es importante: sin él, el resultado dependería del **orden en que JUnit ejecute los tests**, que
no está garantizado. Un test cuyo resultado depende del orden es un test que no vale.

### Paso 9 — Verificación

```bash
./mvnw test            # 98 tests de siempre, sin los de rendimiento
./mvnw -P jetm test    # 43 tests de rendimiento
```

Ambos en verde, y los tiempos se repiten de forma estable entre ejecuciones (se lanzó varias veces para
comprobarlo).

### Ficheros de la rama `JETM`

| Fichero | Estado | Contenido |
|---|---|---|
| `pom.xml` | modificado | 2 dependencias, exclusión de la etiqueta `performance` y perfil `jetm` |
| `src/test/java/.../performance/JetmSupport.java` | nuevo | Calentamiento, medición, lectura de resultados e informe |
| `src/test/java/.../performance/JetmProxyConfig.java` | nuevo | Engancha JETM con Spring AOP a controladores, servicios y mappers |
| `src/test/java/.../performance/ServicePerformanceTest.java` | nuevo | 18 escenarios de servicios, con contexto Spring + JETM |
| `src/test/java/.../performance/ControllerPerformanceTest.java` | nuevo | 23 escenarios de acciones web (controlador → servicio → consultas) |
| `src/test/java/.../performance/CartPerformanceTest.java` | nuevo | 2 tests: escalabilidad y operaciones del carrito |
| `JETM.md` | nuevo | Documentación, resultados y análisis |

---

## 6. Resultados y qué significan

Ejecución en Windows 11 con JDK 21, sobre HSQLDB en memoria. Tiempos en milisegundos.

### 6.1 Servicios

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
| `catalog.searchProductList(fish dog cat)` | 0.072 | 0.018 | 5.331 | **3** |
| `catalog.getItem(EST-1)` | 0.075 | 0.020 | 2.366 | 1 |
| `catalog.getItemListByProduct(FI-SW-01)` | 0.093 | 0.025 | 4.756 | 1 |
| `order.getNextId(ordernum)` | 0.178 | 0.034 | 13.005 | 2 |
| `order.getOrdersByUsername(10 orders)` | 0.179 | 0.052 | 10.721 | 1 |
| `account.insertAccount()` | 0.291 | 0.073 | 4.615 | 3 |
| `account.updateAccount()` | 0.424 | 0.068 | 28.707 | 3 |
| `order.getOrder(3 lines)` | 0.493 | 0.180 | 5.037 | **8** |
| `order.insertOrder(3 lines)` | 0.663 | 0.241 | 6.827 | **10** |

### 6.2 Carrito

| Operación | Media |
|---|---|
| `containsItemId` | < 0.001 |
| `incrementQuantityByItemId` | < 0.001 |
| `setQuantityByItemId` | < 0.001 |
| `addItem` (producto nuevo) | 0.001 |
| `removeItemById` | 0.007 |
| `getSubTotal` — 10 / 100 / 1.000 / 10.000 líneas | 0.005 / 0.021 / 0.048 / 0.289 |

### 6.3 Conclusiones

**1. El tiempo lo marca el número de sentencias SQL, no el código Java.**
Preparar un pedido en memoria (`initOrder`) tarda 0,003 ms; guardarlo, 0,663 ms: **220 veces más**. Correlación
clarísima entre número de consultas y tiempo.

**2. Problema N+1 en `getOrder`** (el hallazgo más importante).
Para un pedido de 3 líneas hace **8 consultas**: el pedido, sus líneas y, **por cada línea**, el artículo y su
stock. Fórmula: **2 + 2n**. Con un pedido de 20 líneas serían 42 consultas. Contra HSQLDB en memoria apenas se
nota, pero contra una base de datos real **cada consulta cuesta un viaje por la red**: 42 viajes frente a 1 es la
diferencia entre milisegundos y décimas de segundo.
**Mejora**: una sola consulta con `JOIN`, o el mapeo anidado de MyBatis.

**3. Una consulta por palabra en la búsqueda.**
`searchProductList("fish dog cat")` tarda el doble que con una palabra, porque el bucle del servicio lanza una
consulta por palabra. **Mejora**: una sola sentencia con varios `OR`.

**4. El coste de la transacción es medible.**
En `insertOrder`, las 10 sentencias suman ~0,53 ms de los ~0,65 ms del servicio. Los ~0,12 ms restantes son abrir
la sesión, la transacción y confirmarla.

**5. La primera consulta de cada transacción es más cara.**
`SequenceMapper.getSequence` (0,100 ms) frente a `SequenceMapper.updateSequence` (0,039 ms), siendo consultas
parecidas sobre la misma tabla diminuta. La diferencia es el coste de abrir la sesión de MyBatis, que se cobra en
la primera.

**6. En el carrito, quitar un producto es lo más caro.**
0,007 ms frente a menos de 0,001 ms del resto. El motivo está en el diseño de `Cart`: mantiene un `Map` (acceso
inmediato) **y** una `List` (recorrido). Las operaciones que solo tocan el mapa son inmediatas; `removeItemById`
tiene que recorrer además la lista para localizar la línea.

**7. El subtotal escala linealmente.**
De 1.000 a 10.000 líneas el tiempo se multiplica por **6**, no por 100: el algoritmo es **O(n)**, como debe ser.
Es menos de 10 porque con pocos elementos pesa más el coste fijo de crear el *stream*.

**8. Los máximos delatan el ruido.**
`updateAccount`: media 0,424 ms, máximo 28,7 ms. No es que una ejecución fuese 70 veces peor: es una pausa del
recolector de basura o del sistema operativo.

**9. La capa web casi no cuesta nada.**
Comparando cada acción con el servicio al que llama, el controlador añade entre 0,002 y 0,01 ms: leer la sesión,
llamar al servicio y rellenar el modelo. Las acciones que no tocan la base de datos (`GET /catalog`, `GET /cart`,
el paso intermedio del pedido) se quedan en 0,001–0,009 ms. **El coste de una petición está en las consultas, no en
el controlador**, y eso tiene una consecuencia práctica: optimizar la capa web no serviría de nada; lo que hay que
atacar es el N+1.

**10. Un efecto colateral interesante.**
`getNextId` **no aparece** como medición anidada dentro de `insertOrder`, aunque se llama desde ahí. El motivo es
una característica clásica de Spring AOP: `insertOrder` llama a `this.getNextId(...)`, y **una llamada interna no
pasa por el proxy**, así que el interceptor no la ve. Es exactamente el mismo motivo por el que un `@Transactional`
no funciona cuando se llama a un método desde dentro de la misma clase. Saber explicar esto demuestra que entiendes
qué estás midiendo y **qué no** estás midiendo.

---

## 7. Cobertura: qué se mide y qué no

| Parte | Estado | Motivo |
|---|---|---|
| `CatalogService` | ✅ 8 de 8 métodos | |
| `AccountService` | ✅ 4 de 4 métodos | |
| `OrderService` | ✅ 4 de 4 métodos | |
| Los 7 mappers | ✅ Medidos de forma anidada | Aparecen dentro de cada servicio que los usa |
| `Cart` | ✅ Las 6 operaciones con lógica | Los demás métodos son getters de una línea |
| `Order.initOrder` | ✅ | |
| Controladores (5 clases) | ✅ 22 acciones web | Se llaman directamente con petición, sesión y modelo simulados |
| JSP y servidor (HTTP, Tomcat) | ❌ | Habría que desplegar la aplicación y medir con el filtro web de JETM |
| Getters y setters del dominio | ❌ (a propósito) | Medir cientos de métodos de una línea generaría ruido y valores por debajo de la resolución del cronómetro |

**Cómo defender que no esté todo**: en rendimiento **no se mide todo, se miden las operaciones críticas**. Un
informe con cientos de filas sería peor que uno con 40, porque lo importante quedaría enterrado. Lo que se mide aquí
es toda la lógica de la aplicación: cada acción web, cada método de servicio y cada consulta a la base de datos.

Lo único que queda fuera es lo que aporta **el servidor**: el tratamiento de la petición HTTP por parte de Tomcat y
el pintado de las JSP. Para medir eso habría que desplegar la aplicación y usar el filtro web de JETM
(`etm.contrib.integration.web`), lo que ya sería una prueba de otro tipo, más cercana a una prueba de carga.

---

## 8. Decisiones y su justificación (chuleta de defensa)

| Decisión | Justificación |
|---|---|
| **JETM y no JMH** | Lo pide el enunciado; además JETM mide operaciones completas **dentro de su contexto real** (Spring + MyBatis + base de datos), que es lo que interesa aquí |
| **Scope `test`** | La medición es parte de las pruebas: no debe acabar en el `.war` desplegado |
| **Medir con AOP y no a mano** | No se toca ni una línea del código de la aplicación: la medición no contamina el código de producción |
| **`NestedMonitor`** | Permite ver **en qué consultas** se va el tiempo: es lo que hace que el análisis sea de caja blanca y lo que destapó el N+1 |
| **`Java15NanoTimer` explícito** | Reloj monótono de nanosegundos; `currentTimeMillis` tiene en Windows una resolución de ~15 ms, inservible aquí |
| **`RootAggregator`** | Sin búfer: los tests necesitan leer las estadísticas justo después de medir |
| **Monitor como *bean*, no `EtmManager`** | Evita el estado global compartido entre clases de test |
| **Etiqueta `@Tag("performance")` + perfil** | Los tests de rendimiento tardan y pueden ser sensibles a la máquina: no deben romper el build de los compañeros |
| **1.000 repeticiones** | Suficiente para que la media sea estable sin que el conjunto tarde demasiado (~10 s) |
| **Calentamiento en dos niveles** | Comprobado: sin el global, el primer test salía 3 veces más lento |
| **Umbrales de 10× lo medido** | Detectan una degradación grave sin provocar fallos aleatorios en otra máquina |
| **Comprobar también el nº de consultas** | Es la parte **estable** del test: no depende del ordenador y detecta el N+1 |
| **Comprobar la proporción 1.000 → 10.000** | Mide la **escalabilidad** (O(n) frente a O(n²)) de forma independiente de la máquina |
| **HSQLDB en memoria** | Ya la usaban los tests de los mappers: no hay que instalar nada y la medición es reproducible |
| **Un campo `volatile`** | Impide que el JIT elimine el cálculo del subtotal por no usarse |

---

## 9. Preguntas probables del profesor

**¿Estática o dinámica? ¿Por qué?**
Dinámica: hay que ejecutar el programa. Sin ejecución no hay tiempos que medir.

**¿Caja blanca o negra?**
Caja blanca. Los puntos de medición se colocan conociendo la estructura interna, y el monitor anidado mide **dentro**
de cada servicio las consultas SQL que lanza. Si solo cronometrásemos peticiones HTTP desde fuera, sería caja negra.

**¿Funcional o no funcional?**
No funcional: mide rendimiento y escalabilidad, no si el resultado es correcto.

**¿Qué diferencia hay entre JETM y SpotBugs en cuanto a cobertura?**
SpotBugs revisa todo el código solo; JETM solo mide lo que tú instrumentas y el test ejecuta. Son modelos
distintos: uno analiza, el otro mide.

**¿Por qué 1.000 repeticiones y no una?**
Porque una sola medición está dominada por el ruido: JIT, recolector de basura, sistema operativo. Se ve en los
datos: el máximo puede ser 70 veces la media.

**¿Qué es el calentamiento y por qué hace falta?**
La JVM interpreta el código al principio y el compilador JIT lo optimiza cuando ve que se repite. Las primeras
ejecuciones son más lentas. Lo medimos: 0,092 ms sin calentar frente a 0,029 ms calentado.

**¿Cómo evitáis que el compilador elimine el código medido?**
Guardando el resultado en un campo `volatile`, que obliga a escribirlo realmente en memoria.

**¿Cómo decidisteis los umbrales?**
Midiendo primero y poniendo después un límite de más de 10 veces lo observado. Un umbral ajustado produciría tests
inestables; uno holgado detecta igualmente una degradación grave.

**¿Y si el test se ejecuta en un ordenador más lento?**
Los umbrales lo absorben. Y la comprobación más importante, la del número de consultas, es **independiente del
hardware**. Lo mismo pasa con la de escalabilidad, porque es una proporción entre dos medidas de la misma máquina.

**¿Por qué un monitor anidado?**
Porque el valor no está en saber que guardar un pedido tarda 0,66 ms, sino en ver que lo hace con 10 sentencias
SQL y cuánto cuesta cada una. Así se encuentra el N+1.

**¿Qué es el problema N+1 y dónde está aquí?**
Cuando se recorre una lista de n elementos y por cada uno se lanza una consulta adicional. En `OrderService.getOrder`:
2 consultas fijas más 2 por cada línea del pedido. Con 3 líneas, 8 consultas.

**¿Por qué no aparece `getNextId` dentro de `insertOrder`?**
Porque `insertOrder` lo llama con `this.getNextId(...)` y las llamadas internas **no pasan por el proxy de Spring**,
así que el interceptor no las ve. Es la misma razón por la que un `@Transactional` no se aplica en una llamada
interna.

**¿Habéis tenido que modificar el código de la aplicación?**
No. La medición se hace con un interceptor AOP sobre los *beans* de Spring, desde los tests.

**¿Cómo medís los controladores sin desplegar la aplicación?**
Llamándolos directamente desde el test con las piezas simuladas de `spring-test`: `MockHttpSession`,
`MockHttpServletRequest` y `ExtendedModelMap`. Se mide controlador, servicio y consultas; queda fuera lo que aporta
el servidor, es decir, HTTP y el pintado de las JSP.

**¿Por qué los tests de rendimiento no se ejecutan en el build normal?**
Porque tardan unos 10 segundos y, al depender de tiempos, podrían fallar en una máquina cargada. No deben bloquear
la compilación del resto del equipo. Se ejecutan con `./mvnw -P jetm test`.

**La base de datos está en memoria: ¿sirven esos números?**
Para comparar operaciones entre sí y para detectar problemas estructurales como el N+1, sí. Para predecir tiempos
de producción, no: con una base de datos real cada consulta añade latencia de red, y precisamente por eso el N+1
sería **mucho** más grave allí.

**¿Qué limitaciones tiene JETM?**
Proyecto abandonado desde hace más de una década; no da percentiles, solo media, mínimo y máximo; no aísla las
mediciones en JVM separadas como JMH; mide un solo hilo, así que no dice nada sobre concurrencia ni carga; y la
propia medición tiene un coste que se nota en operaciones de microsegundos.

**¿Qué usarías hoy en día?**
JMH para micro-*benchmarks*, Micrometer con Prometheus y Grafana para producción, JMeter o Gatling para pruebas de
carga, y JFR o async-profiler para perfilar.

**¿Esto es una prueba de carga?**
No. Es medición de tiempos con **un solo hilo**. Una prueba de carga simula muchos usuarios simultáneos y mide
cómo se degrada el sistema; eso sería JMeter o Gatling.

---

## 10. Cómo hacer la demostración en vivo

```bash
# 1) Los tests normales siguen funcionando igual, sin los de rendimiento
./mvnw test

# 2) Los tests de rendimiento
./mvnw -P jetm test

# 3) Las tablas quedan guardadas
#    target/jetm/services.txt
#    target/jetm/cart.txt
```

Qué enseñar, en este orden:

1. **La tabla anidada de `insertOrder` o `getOrder`**: se ve el escenario, el servicio y cada consulta SQL con su
   número de ejecuciones. Ahí se señala el **N+1** (3 `getItem` y 3 `getInventoryQuantity` por pedido).
2. **El contexto de Spring** (`Context`), explicando que el código de la aplicación **no se ha tocado**.
3. **`JetmSupport.measure`**, explicando calentamiento, `disableCollection` y el `try/finally`.
4. **`assertQueriesPerCall`**, explicando que esa comprobación es la estable y la que detecta regresiones.
5. **La escalabilidad del carrito**: la proporción 1.000 → 10.000 y por qué distingue O(n) de O(n²).
6. Si preguntan por el rigor de la medición: el dato de **0,092 → 0,029 ms** del calentamiento.

---

## 11. Posibles ampliaciones

| Ampliación | Qué aportaría | Coste |
|---|---|---|
| Medir el **servidor y las JSP** desplegando la aplicación y usando el filtro web de JETM | Cubriría lo único que falta: HTTP y el pintado de las vistas | ~2 h |
| **Informe HTML** con `SimpleHtmlRenderer` | Una página navegable, en lugar de una tabla de texto | ~20 min |
| **Consola HTTP** de JETM (`etm.contrib.console`) | Ver las mediciones en el navegador con la aplicación en marcha | ~1 h |
| Corregir el **N+1** y volver a medir | Comparativa antes y después: es la mejor demostración del valor de la herramienta | ~1 h |
| Medir con **más datos** (un pedido de 50 líneas) | Demostraría que el N+1 escala mal, no solo que existe | ~30 min |
| **JMH** como comparación | Contrastar los números de JETM con una herramienta rigurosa | ~2 h |
| Prueba de **carga** con JMeter | Lo que falta de verdad: comportamiento con usuarios concurrentes | ~3 h |

---

## 12. Glosario

| Término | Significado |
|---|---|
| **JETM** | *Java Execution Time Measurement Library* |
| **`EtmPoint`** | Una medición individual: se abre al empezar y se cierra con `collect()` |
| **`EtmMonitor`** | El cronómetro central que crea los puntos y los recoge |
| **`Aggregator`** | Lo que acumula las mediciones y calcula las estadísticas |
| **`Renderer`** | Lo que muestra o exporta el resultado |
| **Monitor anidado** | El que registra qué punto se abrió dentro de qué otro, formando una jerarquía |
| **JIT** (*Just In Time*) | El compilador de la JVM que optimiza en caliente el código que más se usa |
| **Calentamiento** (*warm-up*) | Ejecutar sin medir para que el JIT optimice antes de empezar a medir |
| **Eliminación de código muerto** | Optimización por la que el compilador borra cálculos cuyo resultado no se usa |
| **`volatile`** | Modificador que obliga a escribir el valor en memoria principal; aquí, a no eliminar el cálculo |
| **`System.nanoTime()`** | Reloj monótono de la JVM, el adecuado para medir intervalos |
| **AOP** | Programación orientada a aspectos: añadir comportamiento (aquí, medir) sin tocar el código original |
| **Proxy** | Objeto intermediario que recibe las llamadas, hace algo extra y delega en el objeto real |
| **CGLIB / proxy de clase** | Proxy que hereda de la clase original, necesario cuando no hay interfaz |
| **Problema N+1** | Lanzar una consulta por cada elemento de una lista, en lugar de una sola para todos |
| **O(n) / O(n²)** | Notación de complejidad: el tiempo crece proporcionalmente al número de datos, o a su cuadrado |
| **Test *flaky*** | Test que a veces pasa y a veces falla sin que el código cambie |
| **Percentil (p95)** | Valor por debajo del cual queda el 95 % de las mediciones. JETM no lo calcula |
| **HSQLDB** | Base de datos Java que puede funcionar en memoria, usada en los tests de este proyecto |
