# Pruebas dinámicas con jqwik

## Separación de las pruebas

Las pruebas que ya existían en el repositorio permanecen en
`src/test/java`. Todo el código de jqwik está en la carpeta independiente
`src/jqwik-test/java`, por lo que no se mezclan ambos conjuntos:

```text
src/
├── test/java/       # JUnit existente
└── jqwik-test/java/ # propiedades jqwik
```

El perfil Maven `jqwik`:

1. añade `src/jqwik-test/java` como fuente de pruebas;
2. añade la dependencia `net.jqwik:jqwik`; y
3. limita Surefire a las clases cuyo nombre termina en `PropertiesTest`.

La configuración está en el perfil `jqwik` de [pom.xml](pom.xml), no en la
carpeta de pruebas existente.

## Cómo se han diseñado las pruebas

Son pruebas dinámicas porque jqwik genera automáticamente muchos valores
distintos para cada ejecución (`@ForAll`). Son de caja negra porque solamente
usan la interfaz pública de `Cart`, `Item` y `CartItem`: no acceden a los
mapas, listas ni a otros detalles privados de la implementación.

En [CartPropertiesTest.java](src/jqwik-test/java/org/mybatis/jpetstore/domain/CartPropertiesTest.java)
se comprueban:

- **Pruebas funcionales:** añadir el mismo artículo acumula la cantidad y su
  subtotal; el subtotal de un carrito con varios artículos es la suma de sus
  totales.
- **Prueba no funcional de robustez/carga:** se generan cantidades de hasta
  1.000 unidades y precios de hasta 1.000,00; se comprueba que el carrito
  conserva un estado consistente y calcula el total sin perder datos.

Cada propiedad se ejecuta con múltiples combinaciones aleatorias y jqwik
informa del número de casos, los casos límite y la semilla. Si aparece un
fallo, esa semilla permite reproducirlo.

## Ejecución y evidencia

Ejecutar solamente las pruebas jqwik, sin ejecutar las pruebas JUnit
existentes:

```sh
./mvnw -P jqwik -Dtest=CartPropertiesTest test
```

Para reproducir una ejecución concreta, usar la semilla indicada por jqwik:

```sh
./mvnw -P jqwik -Dtest=CartPropertiesTest -Djqwik.seed=SEED test
```

La ejecución inicial tenía tres propiedades correctas. Para intentar encontrar
fallos se añadieron dos propiedades de exploración:

- cambiar el precio de un artículo después de añadirlo al carrito;
- establecer una cantidad negativa.

Al ejecutarlas, jqwik encuentra contraejemplos mínimos: `CartItem.total` no se
actualiza cuando cambia el precio de `Item`, y `Cart.setQuantityByItemId`
acepta cantidades negativas. Esto es un resultado útil de la prueba de caja
negra: demuestra fallos observables mediante la API pública, sin inspeccionar
la implementación interna.

El número de casos generados aparece además en las líneas
`tries`, `checks` y `seed` del informe de jqwik.

Ejecutar las pruebas normales del repositorio, sin activar jqwik:

```sh
./mvnw test
```


# Fallos resultado de JQWIK

[ERROR] Failures: 

    [ERROR]   CartPropertiesTest.changingAnItemPriceKeepsLineTotalAndSubtotalConsistent:98 
    expected: 0.02
    but was: 0.01

    [ERROR]   CartPropertiesTest.settingQuantityNeverLeavesANegativeCartQuantity:111 
    Expecting actual:
    -1
    to be greater than or equal to:
    0