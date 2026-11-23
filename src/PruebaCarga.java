import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;


public final class PruebaCarga {
    public static void main(String[] args) throws Exception {
        Configuracion c = Configuracion.cargar();
        Registro r = new Registro(4, "PruebaCarga");
        ExecutorService pool = Executors.newFixedThreadPool(30);
        CountDownLatch inicio = new CountDownLatch(1);
        AtomicInteger permitidas = new AtomicInteger(), rechazadas = new AtomicInteger(), errores = new AtomicInteger();
        List<Future<?>> tareas = new ArrayList<>();
        try {
            for (int puerta = 1; puerta <= 3; puerta++) {
                final int p = puerta;
                for (int n = 0; n < 10; n++) {
                    tareas.add(pool.submit(() -> {
                        try {
                            inicio.await();
                            String id = "CARGA-P" + p + "-" + UUID.randomUUID();
                            long lamport = r.enviar("id=" + id + " P" + p + " tipo=ENTRAR");
                            Map<String,String> res = ClienteTcp.llamar(c, Mensajes.crear(
                                    "tipo", "ENTRAR", "token", c.token(), "puerta", Integer.toString(p),
                                    "lamport", Long.toString(lamport), "id", id));
                            r.recibir(Mensajes.reloj(res), "id=" + id + " resultado=" + res.get("resultado"));
                            if ("PERMITIDA".equals(res.get("resultado"))) permitidas.incrementAndGet();
                            else rechazadas.incrementAndGet();
                        } catch (Exception e) {
                            errores.incrementAndGet();
                            r.local("ERROR", "P" + p + " " + e.getClass().getSimpleName());
                        }
                    }));
                }
            }
            System.out.println("[PruebaCarga] Enviando 30 ENTRAR simultaneas (10 por puerta)...");
            inicio.countDown();
            for (Future<?> f : tareas) f.get();
            long l = r.enviar("id=CARGA-ESTADO tipo=ESTADO");
            Map<String,String> estado = ClienteTcp.llamar(c, Mensajes.crear("tipo", "ESTADO", "token", c.token(),
                    "puerta", "1", "lamport", Long.toString(l), "id", "CARGA-ESTADO"));
            r.recibir(Mensajes.reloj(estado), "id=CARGA-ESTADO");
            System.out.printf("RESULTADO: permitidas=%d rechazadas=%d errores=%d dentro=%s aforo=%s%n",
                    permitidas.get(), rechazadas.get(), errores.get(), estado.get("dentro"), estado.get("aforo"));
            System.out.println("NOTA: el modo inseguro puede autorizar >10 aunque el contador final muestre un valor menor por actualizaciones perdidas.");
        } finally {
            pool.shutdownNow();
        }
    }
}
