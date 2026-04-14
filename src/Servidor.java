import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Servidor {

    static volatile boolean activo = true;

    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("Uso: java Servidor <puerto> <palabraClave>");
            System.out.println("Ejemplo: java Servidor 4545 adios");
            return;
        }

        int puerto = 0;
        try {
            puerto = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("El puerto debe ser un numero. Ejemplo: 4545");
            return;
        }

        final String palabraClave = args[1];
        System.out.println("Palabra clave de cierre: [" + palabraClave + "]");

        ServerSocket serverSocket = null;
        Socket socket = null;

        try {
            System.out.print("Iniciando servidor... ");
            serverSocket = new ServerSocket(puerto);
            System.out.println("OK (escuchando en puerto " + puerto + ")");

            System.out.print("Esperando conexion del cliente... ");
            socket = serverSocket.accept();
            System.out.println("OK (cliente conectado)");

            final BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            final PrintWriter salida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            final Scanner teclado = new Scanner(System.in);

            // Hilo que RECIBE mensajes del cliente
            Thread hiloRecibir = new Thread(() -> {
                try {
                    String msg = entrada.readLine();
                    while (activo && msg != null && !msg.equalsIgnoreCase(palabraClave)) {
                        System.out.println("\n[Cliente]: " + msg);
                        msg = entrada.readLine();
                    }
                    if (msg == null) {
                        System.out.println("\n[Sistema] Cliente desconectado.");
                    } else if (msg.equalsIgnoreCase(palabraClave)) {
                        System.out.println("\n[Sistema] Palabra clave recibida. Cerrando... OK");
                    }
                    activo = false;
                } catch (IOException e) {
                    if (activo) System.out.println("\nError recibiendo: " + e.getMessage());
                }
            });

            // Hilo que ENVÍA mensajes al cliente
            Thread hiloEnviar = new Thread(() -> {
                String msg = teclado.nextLine();
                while (activo && !msg.equalsIgnoreCase(palabraClave)) {
                    System.out.print("Enviando mensaje... ");
                    salida.println(msg);
                    System.out.println("OK");
                    msg = teclado.nextLine();
                }
                if (msg.equalsIgnoreCase(palabraClave)) {
                    System.out.print("Enviando mensaje... ");
                    salida.println(msg);
                    System.out.println("OK");
                    System.out.println("[Sistema] Palabra clave enviada. Cerrando... OK");
                }
                activo = false;
            });

            hiloRecibir.start();
            hiloEnviar.start();

            hiloRecibir.join();
            hiloEnviar.interrupt();

            System.out.print("Cerrando Scanner... ");      teclado.close();      System.out.println("OK");
            System.out.print("Cerrando salida... ");       salida.close();       System.out.println("OK");
            System.out.print("Cerrando entrada... ");      entrada.close();      System.out.println("OK");
            System.out.print("Cerrando socket... ");       socket.close();       System.out.println("OK");
            System.out.print("Cerrando servidor... ");     serverSocket.close(); System.out.println("OK");

        } catch (IOException | InterruptedException e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println("Servidor finalizado.");
    }
}
