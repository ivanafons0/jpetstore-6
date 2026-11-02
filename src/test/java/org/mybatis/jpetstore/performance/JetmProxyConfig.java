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

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import etm.contrib.aop.aopalliance.EtmMethodCallInterceptor;
import etm.core.monitor.EtmMonitor;

/**
 * Engancha JETM a los controladores, servicios y mappers con Spring AOP, sin tocar el código de la aplicación.
 * <p>
 * El contexto que importe esta configuración debe declarar un {@link EtmMonitor}.
 */
@Configuration
class JetmProxyConfig {

  /**
   * Interceptor de JETM que mide cada llamada. Los puntos se llaman {@code Clase.metodo}, con el nombre del
   * controlador, servicio o mapper (el nombre por defecto usaría la clase del proxy, que en los mappers es
   * {@code $ProxyNN}).
   *
   * @param etmMonitor
   *          el monitor de JETM
   *
   * @return el interceptor
   */
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

  /**
   * Aplica el interceptor de JETM a todos los controladores, servicios y mappers del contexto.
   *
   * @return el creador de proxies
   */
  @Bean
  static BeanNameAutoProxyCreator etmAutoProxyCreator() {
    BeanNameAutoProxyCreator creator = new BeanNameAutoProxyCreator();
    creator.setBeanNames("*Controller", "*Service", "*Mapper");
    creator.setInterceptorNames("etmInterceptor");
    // Los servicios con @Transactional ya son un proxy de clase: el de JETM tiene que envolverlo también por clase
    creator.setProxyTargetClass(true);
    return creator;
  }

}
