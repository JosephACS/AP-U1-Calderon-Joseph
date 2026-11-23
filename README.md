# AP-U1-Calderon-Joseph — AforoSeguro

**Universidad:** Universidad Técnica Estatal de Quevedo (UTEQ)  
**Asignatura:** Aplicaciones Distribuidas (ISR-701)  
**Nombre usado en esta referencia:** Joseph Alexander Calderón Saltos  
**Tipo de contenido:** IMPLEMENTACIÓN DE REFERENCIA PARA ESTUDIAR Y VERIFICAR EL PLANTEAMIENTO AP-U1.

> **AVISO DE AUTORÍA.** Este código fue generado con asistencia de IA como material de preparación. El planteamiento AP-U1 prohíbe entregar código de IA, reutilizar código ajeno o usar IA durante el examen. **No es una entrega del estudiante ni puede presentarse como código propio.** No se incluyeron commits de sesión, capturas auténticas, credenciales ni evidencias atribuidas al estudiante. Los PDF son plantillas con campos pendientes.

## Qué implementa

- **Un proceso coordinador (ID 0)** y **tres procesos puerta**: P1 Norte, P2 Sur y P3 Este.
- **Sockets TCP con mensajes UTF-8 delimitados por línea** (`clave=valor|clave=valor...`), sin librerías externas ni Internet.
- **Token** cargado desde `config.properties` y rechazo/registro de tokens inválidos.
- **Comandos de puerta:** `entrar`, `salir`, `estado`; se añade `token_malo` para el escenario E2.
- **Control seguro de aforo:** cerrojo compartido (`ReentrantLock`) en el coordinador, contador de 0 a 10 en modo normal.
- **Modo `--inseguro`:** lee el contador, espera 50 ms y escribe el resultado sin proteger la operación completa; permite observar la condición de carrera. *Solo para el experimento E3*.
- **PruebaCarga:** 30 solicitudes concurrentes (10 por cada puerta simulada) y conteo de permitidas/rechazadas/errores.
- **Latidos:** cada puerta envía un `LATIDO` cada 2000 ms; coordinador marca `CAIDA` tras 6000 ms sin latido y `ACTIVA` cuando reaparece.
- **Relojes lógicos de Lamport:** antes de enviar y recibir mensajes se actualizan en cada proceso. Los registros contienen `lamport;id_proceso;evento;detalle`.
- **OrdenTotal:** reúne `logs/Coordinador.log`, `logs/P1.log`, `logs/P2.log`, `logs/P3.log`, ordenando por marca de Lamport e ID de proceso.
- **Sin coordinador:** la puerta no autoriza entradas ni salidas y avisa del problema. Es una decisión de diseño demostrable en E5.

## Requisitos

- **Java JDK 21** (`java -version` y `javac -version`); Windows 10/11 con CMD o PowerShell.
- Para informes PDF: **pdflatex** ya instalado. No hay dependencias Maven ni servicios externos.

## Compilar desde CMD (ubicarse primero en la raíz del proyecto)

```bat
cd /d "RUTA\AP-U1-Calderon-Joseph"
mkdir clases
javac -encoding UTF-8 -d clases src\*.java
```

Si `clases` ya existe, `mkdir` mostrará un aviso que no afecta la compilación. También se puede ejecutar `scripts\compilar.bat`.

## Iniciar procesos — cuatro ventanas distintas

Método rápido: **doble clic en `scripts\abrir_4_procesos.bat`**. Abre el coordinador y las tres puertas.

Método manual (primero desde la carpeta raíz y en cuatro consolas CMD distintas):

```bat
java -cp clases Coordinador
java -cp clases Puerta 1
java -cp clases Puerta 2
java -cp clases Puerta 3
```

En la consola de cada puerta escriba `entrar`, `salir` o `estado`, pulsando Enter. Para probar E2 escriba `token_malo` en una puerta. Para terminar una puerta escriba `fin`; para detener el coordinador use `Ctrl+C` en su consola.

## Mensajes y delimitación

Los mensajes son líneas completas UTF-8; `BufferedReader.readLine()` y `BufferedWriter.newLine()` delimitan cada mensaje. Ejemplo ilustrativo:

```text
tipo=ENTRAR|token=AFORO_LAB_LOCAL_2026|puerta=2|lamport=17|id=P2-ejemplo
tipo=RESPUESTA|id=P2-ejemplo|resultado=PERMITIDA|motivo=|dentro=8|aforo=10|puertas=P1:ACTIVA,P2:ACTIVA,P3:ACTIVA|lamport=19
```

Se acepta más de un mensaje delimitado sobre la misma conexión, pero el cliente de consola abre una conexión por operación. Los IDs de petición permiten emparejar `ENVIO`, `RECEPCION` y `RESPUESTA`. El token de ejemplo es **de laboratorio, no apto para producción**. TCP no cifra el tráfico; este ejercicio es exclusivamente local y educativo.

## Escenario E3: PruebaCarga y modo inseguro

La prueba exige un **coordinador recién iniciado con contador cero**. Antes de cada ejecución detenga el coordinador anterior; la cuenta está en memoria y se reinicia con el proceso.

1. Con el coordinador en modo normal (`java -cp clases Coordinador`):

   ```bat
   java -cp clases PruebaCarga
   ```

   Resultado esperado en el modo normal: **permitidas=10, rechazadas=20, dentro=10** (si no hay solicitudes previas y no hay errores de red).

2. Detenga el coordinador con `Ctrl+C` y vuelva a iniciarlo en modo inseguro:

   ```bat
   java -cp clases Coordinador --inseguro
   ```

3. En otra consola:

   ```bat
   java -cp clases PruebaCarga
   ```

   En este modo puede haber **más de 10 entradas autorizadas**, aunque el contador final no sea mayor que 10, debido a actualizaciones perdidas. La cifra exacta depende del intercalado de hilos y **no debe inventarse**.

## Escenario E4: caída de puerta

Con cuatro procesos activos, cierre P3 (`fin` o `Ctrl+C`). Espere al menos 6 s: el coordinador muestra `P3 CAIDA`. Reinicie `java -cp clases Puerta 3`, espere el nuevo latido: verá `P3 ACTIVA`. En cualquier puerta, `estado` muestra el estado global de las tres puertas.

## Escenario E5: caída de coordinador

Con P1 abierta, detenga la consola del coordinador. En P1 escriba `entrar`. La puerta muestra `NO SE AUTORIZA ENTRAR: coordinador no disponible`. No autorizar sin conocer el contador evita desbordes, pero sacrifica disponibilidad. No se implementa elección de líder ni replicación: el planteamiento **no los exige** en esta arquitectura mínima.

## Escenario E6: orden total

```bat
java -cp clases OrdenTotal
```

Consulte `logs\orden_total.log`. Busque un mismo `id=` de solicitud en `ENVIO` de P1, P2 o P3 y `RECEPCION` del coordinador; debe aparecer primero el envío en el orden lógico. También puede comparar `ENVIO` del coordinador y `RECEPCION` de la puerta. En caso de empate se usa ID de proceso ascendente. Si se reinician procesos, los registros locales conservan el mayor reloj anterior, para evitar retrocesos dentro de cada archivo.

## PDFs y capturas

- `docs/informe.tex` — plantilla de informe **de 2 a 4 páginas**. Las capturas de E0–E6 deben guardarse como `docs/img/E0.png`, ..., `docs/img/E6.png`.
- `docs/entrega_SGA.tex` — plantilla de **una sola página**, con nombre y URL de repositorio **por completar**, que debe convertirse a PDF.
- Compilación desde la raíz del proyecto (requiere `pdflatex`):

```bat
cd docs
pdflatex -interaction=nonstopmode informe.tex
pdflatex -interaction=nonstopmode informe.tex
pdflatex -interaction=nonstopmode entrega_SGA.tex
cd ..
```

Los PDF de plantilla **no constituyen evidencias de ejecución**. Reemplace campos pendientes con capturas y resultados auténticos. `docs/entrega_SGA.pdf` exige poner su URL auténtica antes de usarlo.

## Tabla de reutilización de código

| Archivo en este proyecto | Origen | Archivo / commit de origen | Cambios |
|---|---|---|---|
| Todos los archivos de `src/` | Material de referencia generado con ayuda de IA, no reutilizable como autoría propia en el examen | No aplica | No aplica |

Para una **implementación propia permitida durante el examen**, redacte la tabla conforme al código que efectivamente haya escrito o reutilizado y a las reglas del docente; si no reutilizó nada, escriba `No se reutilizó código`.

## Puntos de la rúbrica relacionados

A: solicitudes + token + delimitación. B: invariantes + 30 hilos + modo inseguro. C: latidos + caída/reactivación. D: Lamport + bitácoras + orden total. Los siete escenarios E0-E6 **requieren capturas personales** (usuario, equipo, fecha/hora). Hay **cuatro preguntas de análisis** en este nuevo PDF (no seis). Para la evaluación se exige código propio, seis commits con código realizados en la sesión, códigos reales de pizarra y acceso a Internet únicamente en la ventana indicada por el docente. El archivo `GUIA_COMANDOS_Y_COMMITS.txt` explica cómo preparar y comprobar ese proceso sin falsificar evidencias.
