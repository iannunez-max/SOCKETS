import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Servidor {

    // Lista de clientes conectados (thread-safe)
    static final List<ClientHandler> clientes = new CopyOnWriteArrayList<>();
    // Cola de mensajes pendientes de responder (orden de llegada)
    static final LinkedBlockingQueue<MensajePendiente> colaMensajes = new LinkedBlockingQueue<>();
    // Flag global del servidor
    static volatile boolean servidorActivo = true;
    // Palabra clave del servidor
    static String palabraClaveServidor;
    // Si ya hubo al menos un cliente conectado
    static volatile boolean huboAlgunCliente = false;

    public static void main(String[] args) {

        if (args.length < 3) {
            System.out.println("Uso: java Servidor <puerto> <palabraClave> <maxClientes>");
            System.out.println("Ejemplo: java Servidor 1234 Cleopatra 5");
            return;
        }

        int puerto;
        int maxClientes;
        try {
            puerto = Integer.parseInt(args[0]);
            maxClientes = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.out.println("Puerto y maxClientes deben ser numeros.");
            return;
        }

        palabraClaveServidor = args[1];

        System.out.println("PORT_SERVIDOR: " + puerto);
        System.out.println("PARAULA_CLAU_SERVIDOR: \"" + palabraClaveServidor + "\"");
        System.out.println("> Server chat at port " + puerto);

        ServerSocket serverSocket = null;

        try {
            System.out.print("> Inicializing server... ");
            serverSocket = new ServerSocket(puerto);
            System.out.println("OK");

            final ServerSocket ss = serverSocket;

            // Hilo que acepta conexiones de clientes
            Thread hiloAceptar = new Thread(() -> {
                int contadorClientes = 0;
                while (servidorActivo) {
                    // Comprobar si hay espacio para mas clientes
                    if (clientes.size() >= maxClientes) {
                        try { Thread.sleep(200); } catch (InterruptedException e) { break; }
                        continue;
                    }
                    try {
                        ss.setSoTimeout(500);
                        Socket socketCliente = ss.accept();
                        contadorClientes++;
                        int idCliente = contadorClientes;

                        System.out.println("> Connection from client " + idCliente + " ... OK");

                        ClientHandler handler = new ClientHandler(socketCliente, idCliente);
                        clientes.add(handler);
                        huboAlgunCliente = true;
                        handler.start();

                    } catch (SocketTimeoutException e) {
                        // Timeout normal, seguir esperando
                    } catch (IOException e) {
                        if (servidorActivo) {
                            // Error al aceptar, pero seguimos
                        }
                    }
                }
            });
            hiloAceptar.setDaemon(true);
            hiloAceptar.start();

            // Hilo que detecta si nos quedamos sin clientes (despues de haber tenido al menos uno)
            Thread hiloMonitor = new Thread(() -> {
                while (servidorActivo) {
                    try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                    if (huboAlgunCliente && clientes.isEmpty()) {
                        System.out.println("> No clients connected. Closing server...");
                        servidorActivo = false;
                        break;
                    }
                }
            });
            hiloMonitor.setDaemon(true);
            hiloMonitor.start();

            // Esperar al primer cliente
            while (!huboAlgunCliente && servidorActivo) {
                Thread.sleep(200);
            }

            if (huboAlgunCliente) {
                System.out.println("> Inicializing chat... OK");
            }

            Scanner teclado = new Scanner(System.in);

            // Bucle principal: el servidor responde en orden de llegada
            while (servidorActivo) {
                MensajePendiente mp = null;
                try {
                    mp = colaMensajes.poll(300, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    break;
                }

                if (mp == null) continue;

                // Verificar que el cliente sigue activo
                if (!mp.handler.estaActivo()) continue;

                // Mostrar mensaje recibido
                System.out.println("#Rebut del client " + mp.handler.idCliente + ": " + mp.mensaje);

                // Comprobar si el mensaje contiene la palabra clave DEL PROPIO CLIENTE
                if (mp.mensaje.equalsIgnoreCase(mp.handler.palabraClaveCliente)) {
                    System.out.println("> Client " + mp.handler.idCliente + " keyword detected!");
                    mp.handler.cerrar();
                    clientes.remove(mp.handler);
                    continue;
                }

                // Comprobar si el mensaje contiene la palabra clave DEL SERVIDOR
                if (mp.mensaje.equalsIgnoreCase(palabraClaveServidor)) {
                    System.out.println("> Server keyword detected from client " + mp.handler.idCliente + "!");
                    // La palabra clave del servidor usada por un cliente NO cierra nada
                    // (solo el servidor puede usar su propia palabra clave)
                }

                // Pedir respuesta del servidor
                System.out.print("#Enviar al client " + mp.handler.idCliente + ": ");
                if (!teclado.hasNextLine()) break;
                String respuesta = teclado.nextLine();

                // Comprobar si el servidor usa la palabra clave de ESTE cliente
                if (mp.handler.palabraClaveCliente != null &&
                    respuesta.equalsIgnoreCase(mp.handler.palabraClaveCliente)) {
                    mp.handler.enviar(respuesta);
                    System.out.println("> Client " + mp.handler.idCliente + " keyword detected!");
                    mp.handler.cerrar();
                    clientes.remove(mp.handler);
                    continue;
                }

                // Comprobar si el servidor usa SU PROPIA palabra clave
                if (respuesta.equalsIgnoreCase(palabraClaveServidor)) {
                    System.out.println("> Server keyword detected!");
                    System.out.print("> Closing chat... ");
                    // Cerrar todos los clientes
                    for (ClientHandler ch : clientes) {
                        ch.enviar(respuesta);
                        ch.cerrar();
                    }
                    clientes.clear();
                    System.out.println("OK");
                    servidorActivo = false;
                    break;
                }

                // Enviar respuesta normal
                mp.handler.enviar(respuesta);
            }

            // Cerrar todo
            for (ClientHandler ch : clientes) {
                ch.cerrar();
            }
            clientes.clear();

            teclado.close();
            serverSocket.close();

            System.out.print("> Closing server... ");
            System.out.println("OK");
            System.out.println("> Bye!");

        } catch (IOException | InterruptedException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    // Clase interna para manejar cada cliente en su propio hilo
    static class ClientHandler extends Thread {
        Socket socket;
        int idCliente;
        BufferedReader entrada;
        PrintWriter salida;
        volatile boolean activo = true;
        String palabraClaveCliente = null; // Se recibe como primer mensaje

        ClientHandler(Socket socket, int idCliente) {
            this.socket = socket;
            this.idCliente = idCliente;
        }

        boolean estaActivo() {
            return activo;
        }

        void enviar(String msg) {
            if (salida != null && activo) {
                salida.println(msg);
            }
        }

        void cerrar() {
            activo = false;
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) { /* ignorar */ }
        }

        @Override
        public void run() {
            try {
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                salida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

                // El cliente envia su palabra clave como primer mensaje
                palabraClaveCliente = entrada.readLine();
                if (palabraClaveCliente == null) {
                    activo = false;
                    return;
                }

                // Leer mensajes del cliente y ponerlos en la cola
                String msg;
                while (activo && servidorActivo && (msg = entrada.readLine()) != null) {
                    colaMensajes.put(new MensajePendiente(this, msg));
                }

            } catch (IOException | InterruptedException e) {
                // Cliente desconectado
            } finally {
                activo = false;
                clientes.remove(this);
            }
        }
    }

    // Clase para encolar mensajes con referencia al cliente que lo envio
    static class MensajePendiente {
        ClientHandler handler;
        String mensaje;

        MensajePendiente(ClientHandler handler, String mensaje) {
            this.handler = handler;
            this.mensaje = mensaje;
        }
    }
}