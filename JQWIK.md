# Pruebas independientes con jqwik

Las pruebas existentes del repositorio permanecen en `src/test/java`.
Las propiedades jqwik se mantienen aparte en:

```text
src/jqwik-test/java
```

El perfil Maven `jqwik` añade esa carpeta como fuente de pruebas, activa la
dependencia jqwik y limita Surefire a las clases `*PropertiesTest`.

Ejecutar únicamente las propiedades jqwik:

```sh
./mvnw -P jqwik -Dtest=CartPropertiesTest test
```

Ejecutar las pruebas normales del repositorio, sin jqwik:

```sh
./mvnw test
```

Las propiedades generan múltiples combinaciones de cantidades y precios para
comprobar que el carrito conserva las cantidades y calcula correctamente los
totales.
