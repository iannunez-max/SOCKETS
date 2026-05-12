import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Cliente {

    static volatile boolean activo = true;

    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("Uso: java Cliente <puerto> <palabraClave>");
            System.out.println("Ejemplo: java Cliente 1234 \"Marc Antoni\"");
            return;
        }

        int puerto;
        try {
            puerto = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("El puerto debe ser un numero.");
            return;
        }

        final String palabraClave = args[1];

        System.out.println("PORT_SERVIDOR: " + puerto);
        System.out.println("PARAULA_CLAU_CLIENT: \"" + palabraClave + "\"");

        Socket socket = null;

        try {
            System.out.print("> Client chat to port " + puerto + "\n");
            System.out.print("> Inicializing client... ");
            socket = new Socket("localhost", puerto);
            System.out.println("OK");

            final BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            final PrintWriter salida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            final Scanner teclado = new Scanner(System.in);

            // Enviar palabra clave al servidor como primer mensaje (protocolo)
            salida.println(palabraClave);

            System.out.println("> Inicializing chat... OK");

            final Socket socketFinal = socket;

            // Hilo que RECIBE mensajes del servidor
            Thread hiloRecibir = new Thread(() -> {
                try {
                    String msg;
                    while (activo && (msg = entrada.readLine()) != null) {
                        // Comprobar si el mensaje recibido es MI palabra clave
                        if (msg.equalsIgnoreCase(palabraClave)) {
                            System.out.println("#Rebut del servidor: " + msg);
                            System.out.println("> Client keyword detected!");
                            activo = false;
                            break;
                        }
                        System.out.println("#Rebut del servidor: " + msg);
                    }
                    if (msg == null && activo) {
                        System.out.println("> Server disconnected.");
                    }
                    activo = false;
                } catch (IOException e) {
                    if (activo) {
                        activo = false;
                    }
                }
            });

            // Hilo que ENVÍA mensajes al servidor
            Thread hiloEnviar = new Thread(() -> {
                try {
                    while (activo) {
                        if (!teclado.hasNextLine()) break;
                        String msg = teclado.nextLine();
                        System.out.print("#Enviar al servidor: " + msg + "\n");
                        salida.println(msg);

                        // Comprobar si YO envié mi palabra clave
                        if (msg.equalsIgnoreCase(palabraClave)) {
                            System.out.println("> Client keyword detected!");
                            activo = false;
                            break;
                        }
                    }
                } catch (Exception e) {
                    activo = false;
                }
            });

            hiloRecibir.start();
            hiloEnviar.start();

            hiloRecibir.join();
            hiloEnviar.interrupt();

            System.out.print("> Closing chat... ");
            System.out.println("OK");

            teclado.close();
            salida.close();
            entrada.close();

            System.out.print("> Closing client... ");
            socket.close();
            System.out.println("OK");

            System.out.println("> Bye!");

        } catch (IOException | InterruptedException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}