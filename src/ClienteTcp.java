import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ClienteTcp {
    private ClienteTcp() { }

    public static Map<String,String> llamar(Configuracion c, String mensaje) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(c.host(), c.puerto()), c.timeoutMs());
            socket.setSoTimeout(c.timeoutMs());
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out.write(mensaje);
            out.newLine();
            out.flush();
            String linea = in.readLine();
            if (linea == null) throw new IOException("El servidor cerro la conexion sin responder");
            try { return Mensajes.leer(linea); }
            catch (IllegalArgumentException e) { throw new IOException("Respuesta invalida", e); }
        }
    }
}
