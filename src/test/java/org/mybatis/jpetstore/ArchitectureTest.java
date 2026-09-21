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
package org.mybatis.jpetstore;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;

@AnalyzeClasses(packages = "org.mybatis.jpetstore", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  // comprobar dependencias entre capas, por ejemplo que los controladores no accedan a los mappers directamente, sino
  // que pasen por el servicio
  @ArchTest
  static final ArchRule layers = layeredArchitecture().consideringOnlyDependenciesInLayers().layer("Web")
      .definedBy("..web..").layer("Service").definedBy("..service..").layer("Mapper").definedBy("..mapper..")
      .layer("Domain").definedBy("..domain..").whereLayer("Web").mayNotBeAccessedByAnyLayer().whereLayer("Service")
      .mayOnlyBeAccessedByLayers("Web").whereLayer("Mapper").mayOnlyBeAccessedByLayers("Service").whereLayer("Domain")
      .mayOnlyBeAccessedByLayers("Web", "Service", "Mapper");

  // comprobar que los controladores no accedan a los mappers directamente, sino que pasen por el servicio
  @ArchTest
  static final ArchRule controllersShouldNotUseMappers = noClasses().that().resideInAPackage("..web..").should()
      .dependOnClassesThat().resideInAPackage("..mapper..").because("controllers must go through the service layer");

  // comprobar que los objetos de dominio son POJOs y no dependen de otras capas
  @ArchTest
  static final ArchRule domainShouldBeIndependent = classes().that().resideInAPackage("..domain..").should()
      .onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..").because("domain objects are plain POJOs");

  // comprobar que las clases principales de web.controllers terminan en Controller y llevan @Controller
  @ArchTest
  static final ArchRule controllersNaming = classes().that().resideInAPackage("..web.controllers..").and()
      .areTopLevelClasses().should().haveSimpleNameEndingWith("Controller").andShould()
      .beAnnotatedWith(Controller.class);

  // comprobar que toda clase con @Controller está en web.controllers, que es donde Spring la busca
  @ArchTest
  static final ArchRule controllersShouldResideInControllersPackage = classes().that()
      .areAnnotatedWith(Controller.class).should().resideInAPackage("..web.controllers..")
      .because("Spring only scans that package for controllers");

  // comprobar que las clases principales de service terminan en Service y llevan @Service
  @ArchTest
  static final ArchRule servicesNaming = classes().that().resideInAPackage("..service..").and().areTopLevelClasses()
      .should().haveSimpleNameEndingWith("Service").andShould().beAnnotatedWith(Service.class);

  // comprobar que toda clase con @Service está en service, que es donde Spring la busca
  @ArchTest
  static final ArchRule servicesShouldResideInServicePackage = classes().that().areAnnotatedWith(Service.class).should()
      .resideInAPackage("..service..").because("Spring only scans that package for services");

  // comprobar que los servicios solo dependen del JDK, del dominio, de los mappers y de las anotaciones de Spring que
  // necesitan (@Service y @Transactional), para que no usen nada de HTTP
  @ArchTest
  static final ArchRule servicesShouldOnlyDependOnAllowedPackages = classes().that().resideInAPackage("..service..")
      .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..", "..mapper..", "..service..",
          "org.springframework.stereotype..", "org.springframework.transaction..")
      .because("services must not know about HTTP");

  // comprobar que @Transactional (en métodos o en clases) solo se usa en los servicios
  @ArchTest
  static final ArchRule transactionalOnlyInServices = noClasses().that().resideOutsideOfPackage("..service..").should()
      .dependOnClassesThat().haveSimpleName("Transactional")
      .because("transaction boundaries belong to the service layer");

  // comprobar que los mappers son interfaces terminadas en Mapper, para que MyBatis las implemente con su XML
  @ArchTest
  static final ArchRule mappersShouldBeInterfaces = classes().that().resideInAPackage("..mapper..").should()
      .beInterfaces().andShould().haveSimpleNameEndingWith("Mapper");

  // comprobar que los mappers solo dependen del JDK y del dominio, para que no usen Spring, HTTP ni otras capas
  @ArchTest
  static final ArchRule mappersShouldOnlyDependOnDomain = classes().that().resideInAPackage("..mapper..").should()
      .onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..", "..mapper..")
      .because("mappers only translate between the database and domain objects");

  // comprobar que no hay dependencias cíclicas entre los paquetes de primer nivel (web, service, mapper, domain...)
  @ArchTest
  static final ArchRule noCycles = slices().matching("org.mybatis.jpetstore.(*)..").should().beFreeOfCycles();

}
