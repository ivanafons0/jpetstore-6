/*
 *    Copyright 2010-2026 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.jpetstore.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import etm.core.aggregation.Aggregate;
import etm.core.monitor.EtmMonitor;
import etm.core.monitor.EtmPoint;
import etm.core.renderer.SimpleTextRenderer;

/**
 * Utilidades comunes de los tests de rendimiento con JETM.
 */
final class JetmSupport {

  /** Ejecuciones sin medir, para que el JIT optimice el código antes de empezar a medir. */
  static final int WARMUP_ITERATIONS = 200;

  /** Ejecuciones medidas de cada operación. */
  static final int MEASURED_ITERATIONS = 1000;

  private JetmSupport() {
  }

  /**
   * Mide una operación con JETM: primero la calienta sin medir y después la ejecuta {@link #MEASURED_ITERATIONS} veces,
   * cada una dentro de un punto de medición con el nombre indicado.
   *
   * @param monitor
   *          el monitor de JETM
   * @param pointName
   *          el nombre del punto de medición
   * @param operation
   *          la operación a medir
   */
  static void measure(EtmMonitor monitor, String pointName, Runnable operation) {
    measure(monitor, pointName, operation, null);
  }

  /**
   * Como {@link #measure(EtmMonitor, String, Runnable)}, pero deshaciendo el efecto de la operación entre una medición
   * y la siguiente. La restauración queda fuera de la medición.
   *
   * @param monitor
   *          el monitor de JETM
   * @param pointName
   *          el nombre del punto de medición
   * @param operation
   *          la operación a medir
   * @param restore
   *          cómo dejar el estado igual que antes de la operación, o {@code null} si no hace falta
   */
  static void measure(EtmMonitor monitor, String pointName, Runnable operation, Runnable restore) {
    // Calentamiento: con la recogida desactivada, tampoco se guardan las mediciones anidadas (Spring AOP)
    monitor.disableCollection();
    try {
      for (int i = 0; i < WARMUP_ITERATIONS; i++) {
        operation.run();
        if (restore != null) {
          restore.run();
        }
      }
    } finally {
      monitor.enableCollection();
    }

    for (int i = 0; i < MEASURED_ITERATIONS; i++) {
      EtmPoint point = monitor.createPoint(pointName);
      try {
        operation.run();
      } finally {
        point.collect();
      }
      if (restore != null) {
        // La restauración no es parte de la medición: si llama a código instrumentado (Spring AOP), sus mediciones
        // no deben aparecer en el informe
        monitor.disableCollection();
        try {
          restore.run();
        } finally {
          monitor.enableCollection();
        }
      }
    }
  }

  /**
   * Devuelve el resultado agregado (nº de mediciones, media, mínimo, máximo...) de un punto de medición. Para los
   * puntos anidados se indica el camino completo, por ejemplo: escenario, servicio y mapper.
   *
   * @param monitor
   *          el monitor de JETM
   * @param path
   *          los nombres de los puntos, del más externo al más interno
   *
   * @return el resultado agregado
   */
  static Aggregate aggregate(EtmMonitor monitor, String... path) {
    AtomicReference<Aggregate> result = new AtomicReference<>();
    monitor.render(points -> result.set(find(points, path)));
    assertThat(result.get()).as("mediciones de %s", String.join(" > ", path)).isNotNull();
    return result.get();
  }

  private static Aggregate find(Map<?, ?> points, String... path) {
    Map<?, ?> level = points;
    Aggregate aggregate = null;
    for (String name : path) {
      aggregate = level == null ? null : (Aggregate) level.get(name);
      if (aggregate == null) {
        return null;
      }
      level = aggregate.getChilds();
    }
    return aggregate;
  }

  /**
   * Muestra la tabla de resultados de JETM por consola y la guarda en {@code target/jetm/<reportName>.txt}.
   *
   * @param monitor
   *          el monitor de JETM
   * @param reportName
   *          el nombre del informe
   *
   * @throws IOException
   *           si no se puede escribir el fichero
   */
  static void writeReport(EtmMonitor monitor, String reportName) throws IOException {
    StringWriter table = new StringWriter();
    monitor.render(new SimpleTextRenderer(table, Locale.ROOT));
    System.out.println(table);

    Path file = Paths.get("target", "jetm", reportName + ".txt");
    Files.createDirectories(file.getParent());
    Files.write(file, table.toString().getBytes(StandardCharsets.UTF_8));
  }

}
