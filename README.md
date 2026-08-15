# EmiMobControl

Mod Fabric para Minecraft 1.21.1 que reduce entidades innecesarias y convierte
los spawners vanilla en granjas compactas. Está diseñado para convivir con
Cobblemon, RCT y los NPC del servidor sin eliminarlos.

## Instalación

Instala el mismo JAR en el servidor y en todos los clientes. Requiere Fabric API,
Java 21 y Minecraft 1.21.1.

## Granjas de spawner

1. Rompe un spawner vanilla con un pico que tenga **Toque de Seda**.
2. Recibirás una `Granja de <criatura>` que conserva el tipo de mob.
3. Colócala encima de una tolva conectada a un cofre.
4. El bloque genera los drops de la tabla vanilla sin crear mobs físicos.
5. La experiencia aparece como `Esencia de experiencia`; usa cada esencia para
   recibir 5 puntos de XP.

Jefes, Pokémon, NPC y entidades de otros mods no pueden convertirse. Romper la
granja vuelve a entregarla solamente con Toque de Seda; su inventario interno
siempre cae para evitar pérdidas.

## Mejoras mediante crafteo

Coloca la granja en el centro de una mesa de crafteo y rodéala con ocho unidades
del material del siguiente tier. La receta conserva el mob guardado y solamente
permite avanzar un tier cada vez:

```text
M M M
M G M    G = granja de spawner
M M M    M = material del siguiente tier
```

| Tier resultante | 8 materiales requeridos | Ciclo | Muertes simuladas |
|---|---|---:|---:|
| Básico | Ninguno | 30 s | 1 |
| Hierro | Lingotes de hierro | 24 s | 2 |
| Oro | Lingotes de oro | 18 s | 3 |
| Esmeralda | Esmeraldas | 13 s | 4 |
| Diamante | Diamantes | 9 s | 5 |
| Netherita | Lingotes de netherita | 5 s | 7 |

El tier queda guardado en el objeto y se conserva al romper y volver a colocar
la granja con Toque de Seda.

Sobre cada granja aparece un texto con:

- criatura contenida;
- tier actual;
- tolva conectada, desconectada o salida llena.

El bloque también muestra un borde luminoso con el color de su tier. El
renderizado incluye compatibilidad automática con EntityCulling, además de
Sodium e Iris.

## Limpieza segura

Por defecto se ejecuta cada 15 minutos y elimina únicamente monstruos vanilla
que estén a más de 64 bloques de un jugador. No elimina Pokémon, NPC, criaturas
de otros mods, mascotas, mobs con nombre, mobs atados, aldeanos, gólems ni mobs
marcados como persistentes. Los animales pasivos no se limpian por defecto.

Configuración: `config/emimobcontrol.json`.

Comandos para operadores:

```text
/emimobcontrol status
/emimobcontrol reload
/emimobcontrol cleanup now
```

La limpieza usa `discard` y no deja drops ni XP, evitando convertirla en una
granja global accidental.
