package com.servidor;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

public class Servidor2025 {

    private static final String ARCHIVO_USUARIOS = "usuarios.txt";
    private static Map<String, String> usuarios = cargarUsuarios();
    private static final String ARCHIVO_MENSAJES = "mensajes.txt";
    private static Map<String, PrintWriter> clientesConectados = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket servidor = new ServerSocket(8080)) {
            System.out.println("Servidor iniciado. Esperando conexión...");

            while (true) {
                Socket cliente = servidor.accept();
                System.out.println("Cliente conectado desde: " + cliente.getInetAddress().getHostAddress());
                new Thread(new ManejadorCliente(cliente)).start();
            }
        } catch (IOException e) {
            System.err.println("Error en la conexión del servidor: " + e.getMessage());
        }
    }

    private static Map<String, String> cargarUsuarios() {
        Map<String, String> usuariosCargados = new HashMap<>();
        File archivo = new File(ARCHIVO_USUARIOS);
        
        if (!archivo.exists()) {
            return usuariosCargados;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(":");
                if (partes.length == 2) {
                    usuariosCargados.put(partes[0], partes[1]);
                }
            }
        } catch (IOException e) {
            System.err.println("Error leyendo archivo de usuarios: " + e.getMessage());
        }
        return usuariosCargados;
    }

    private static synchronized void guardarUsuarios() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(ARCHIVO_USUARIOS))) {
            for (Map.Entry<String, String> entrada : usuarios.entrySet()) {
                pw.println(entrada.getKey() + ":" + entrada.getValue());
            }
        } catch (IOException e) {
            System.err.println("Error escribiendo archivo de usuarios: " + e.getMessage());
        }
    }
    
    private static synchronized void guardarMensaje(String remitente, String destinatario, String mensaje) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(ARCHIVO_MENSAJES, true))) {
            pw.println("de:" + remitente + ":para:" + destinatario + ":mensaje:" + mensaje);
        } catch (IOException e) {
            System.err.println("Error escribiendo en el archivo de mensajes: " + e.getMessage());
        }
    }

    private static void enviarMensajePrivado(String remitente, String destinatario, String mensaje) {
        synchronized (clientesConectados) {
            PrintWriter escritorDestinatario = clientesConectados.get(destinatario);
            PrintWriter escritorRemitente = clientesConectados.get(remitente);

            if (escritorDestinatario != null) {
                escritorDestinatario.println("[Privado de " + remitente + "]: " + mensaje);
            } else {
                escritorRemitente.println("El usuario '" + destinatario + "' no está conectado. El mensaje se ha guardado en su buzón.");
            }
        }
    }

    static class ManejadorCliente implements Runnable {
        private Socket cliente;
        private PrintWriter escritor;
        private BufferedReader lector;
        private String usuarioAutenticado = null;

        public ManejadorCliente(Socket cliente) {
            this.cliente = cliente;
        }

        public void run() {
            try {
                escritor = new PrintWriter(cliente.getOutputStream(), true);
                lector = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
                
                boolean autenticado = false;
                while (!autenticado) {
                    escritor.println("Bienvenido. Escribe 'login' para iniciar sesion o 'register' para crear cuenta.");
                    String accion = lector.readLine();

                    if (accion == null) {
                        break;
                    }
                    
                    if ("login".equalsIgnoreCase(accion)) {
                        autenticado = manejarLogin();
                    } else if ("register".equalsIgnoreCase(accion)) {
                        manejarRegistro();
                    } else {
                        escritor.println("Accion no reconocida. Intenta de nuevo.");
                    }
                }
                
                if (autenticado) {
                    synchronized (clientesConectados) {
                        clientesConectados.put(this.usuarioAutenticado, escritor);
                    }
                    
                    String opcion;
                    escritor.println("Escribe 'jugar', 'chat', 'buzon', 'borrar' o 'eliminar'.");
                    while ((opcion = lector.readLine()) != null) {
                         if ("jugar".equalsIgnoreCase(opcion)) {
                            jugarJuego();
                            escritor.println("Escribe 'jugar', 'chat', 'buzon', 'borrar' o 'eliminar'.");
                        } else if ("chat".equalsIgnoreCase(opcion)) {
                            manejarChat();
                            escritor.println("Escribe 'jugar', 'chat', 'buzon', 'borrar' o 'eliminar'.");
                        } else if ("buzon".equalsIgnoreCase(opcion)) {
                            cargarBuzon();
                            escritor.println("Escribe 'jugar', 'chat', 'buzon', 'borrar' o 'eliminar'.");
                        } else if ("borrar".equalsIgnoreCase(opcion)) {
                            borrarMensaje();
                            escritor.println("Escribe 'jugar', 'chat', 'buzon', 'borrar' o 'eliminar'.");
                        } else if ("eliminar".equalsIgnoreCase(opcion)) {
                            eliminarCuenta();
                            break; 
                        } else {
                            escritor.println("Opcion no reconocida. Escribe 'jugar', 'chat', 'buzon', 'borrar' o 'eliminar'.");
                        }
                    }
                }
                
            } catch (IOException e) {
                System.err.println("Error en la comunicacion con el cliente: " + e.getMessage());
            } finally {
                synchronized (clientesConectados) {
                    if (this.usuarioAutenticado != null) {
                        clientesConectados.remove(this.usuarioAutenticado);
                    }
                }
                try {
                    if (lector != null) lector.close();
                    if (escritor != null) escritor.close();
                    if (cliente != null) cliente.close();
                } catch (IOException e) {
                    System.err.println("Error al cerrar recursos del cliente: " + e.getMessage());
                }
            }
        }
        
        private boolean manejarLogin() throws IOException {
            escritor.println("Introduce tu usuario:");
            String usuario = lector.readLine();
            escritor.println("Introduce tu contrasena:");
            String contrasena = lector.readLine();

            if (usuarios.containsKey(usuario) && usuarios.get(usuario).equals(contrasena)) {
                this.usuarioAutenticado = usuario;
                escritor.println("Inicio de sesion exitoso. ¡Bienvenido " + usuario + "!");
                return true;
            } else {
                escritor.println("Usuario o contrasena incorrectos.");
                return false;
            }
        }

        private void manejarRegistro() throws IOException {
            escritor.println("Elige un usuario:");
            String nuevoUsuario = lector.readLine();

            if (usuarios.containsKey(nuevoUsuario)) {
                escritor.println("El usuario ya existe. Intenta iniciar sesion.");
            } else {
                escritor.println("Elige una contrasena:");
                String nuevaContrasena = lector.readLine();
                usuarios.put(nuevoUsuario, nuevaContrasena);
                guardarUsuarios();
                escritor.println("Registro exitoso. Ahora puedes iniciar sesion.");
            }
        }
        
        private void jugarJuego() throws IOException {
            Random random = new Random();
            int numeroSecreto = random.nextInt(10) + 1;
            int intentos = 0;
            boolean adivinado = false;

            escritor.println("¡Adivina el numero! He generado un numero entre 1 y 10.");
            
            while (!adivinado) {
                try {
                    String intentoStr = lector.readLine();
                    if (intentoStr == null) {
                        break;
                    }
                    
                    int intento = Integer.parseInt(intentoStr);
                    intentos++;

                    if (intento < numeroSecreto) {
                        escritor.println("El numero es mayor.");
                    } else if (intento > numeroSecreto) {
                        escritor.println("El numero es menor.");
                    } else {
                        escritor.println("¡Correcto! Adivinaste el numero " + numeroSecreto + " en " + intentos + " intentos.");
                        adivinado = true;
                    }
                } catch (NumberFormatException e) {
                    escritor.println("Entrada invalida. Por favor, introduce un numero.");
                }
            }
        }
        
        private void manejarChat() throws IOException {
            escritor.println("Has entrado al chat. Escribe el nombre del destinatario o 'salir' para volver.");
            String destinatario = lector.readLine();
            if ("salir".equalsIgnoreCase(destinatario)) {
                return;
            }

            escritor.println("Escribe el mensaje:");
            String mensaje = lector.readLine();

            if (destinatario != null && !destinatario.isEmpty() && mensaje != null && !mensaje.isEmpty()) {
                guardarMensaje(this.usuarioAutenticado, destinatario, mensaje);
                enviarMensajePrivado(this.usuarioAutenticado, destinatario, mensaje);
            } else {
                escritor.println("Operación cancelada o datos incompletos.");
            }
        }

        private void borrarMensaje() throws IOException {
            List<String> mensajesDelBuzon = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_MENSAJES))) {
                String linea;
                int contador = 1;
                escritor.println("--- Buzón de " + this.usuarioAutenticado + " ---");
                while ((linea = br.readLine()) != null) {
                    if (linea.contains(":para:" + this.usuarioAutenticado + ":")) {
                        String[] partes = linea.split(":mensaje:");
                        String mensaje = partes[1];
                        escritor.println(contador + ". " + mensaje);
                        mensajesDelBuzon.add(linea);
                        contador++;
                    }
                }
                escritor.println("-------------------------");
            } catch (IOException e) {
                escritor.println("No hay mensajes en tu buzón.");
                return;
            }

            if (mensajesDelBuzon.isEmpty()) {
                escritor.println("No hay mensajes que puedas borrar.");
                return;
            }

            escritor.println("Escribe el numero del mensaje que deseas borrar o 'salir' para cancelar.");
            String opcion = lector.readLine();
            if ("salir".equalsIgnoreCase(opcion)) {
                return;
            }
            
            try {
                int numeroMensaje = Integer.parseInt(opcion);
                if (numeroMensaje > 0 && numeroMensaje <= mensajesDelBuzon.size()) {
                    mensajesDelBuzon.remove(numeroMensaje - 1);
                    reescribirArchivoMensajes(mensajesDelBuzon);
                    escritor.println("Mensaje borrado exitosamente.");
                } else {
                    escritor.println("Numero de mensaje inválido.");
                }
            } catch (NumberFormatException e) {
                escritor.println("Entrada inválida. Por favor, introduce un numero.");
            }
        }

        private void reescribirArchivoMensajes(List<String> mensajes) {
            synchronized (Servidor2025.class) {
                try (PrintWriter pw = new PrintWriter(new FileWriter(ARCHIVO_MENSAJES))) {
                    for (String mensaje : mensajes) {
                        pw.println(mensaje);
                    }
                } catch (IOException e) {
                    System.err.println("Error reescribiendo el archivo de mensajes: " + e.getMessage());
                }
            }
        }

        private void eliminarCuenta() throws IOException {
            escritor.println("ADVERTENCIA: Vas a eliminar tu cuenta. Todos tus mensajes enviados serán borrados. Escribe 'confirmar' para continuar.");
            String confirmacion = lector.readLine();
            
            if ("confirmar".equalsIgnoreCase(confirmacion)) {
                List<String> mensajesRestantes = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_MENSAJES))) {
                    String linea;
                    while ((linea = br.readLine()) != null) {
                        if (!linea.startsWith("de:" + this.usuarioAutenticado + ":")) {
                            mensajesRestantes.add(linea);
                        }
                    }
                    reescribirArchivoMensajes(mensajesRestantes);
                } catch (IOException e) {
                    System.err.println("No se pudo leer el archivo de mensajes para eliminar los de la cuenta.");
                }

                usuarios.remove(this.usuarioAutenticado);
                guardarUsuarios();
                
                escritor.println("Tu cuenta y todos tus mensajes enviados han sido eliminados. Desconectando...");
                this.usuarioAutenticado = null; 
            } else {
                escritor.println("Operación de eliminación de cuenta cancelada.");
            }
        }
        
        private void cargarBuzon() throws IOException {
            try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_MENSAJES))) {
                String linea;
                int contador = 1;
                escritor.println("--- Buzón de " + this.usuarioAutenticado + " ---");
                while ((linea = br.readLine()) != null) {
                    if (linea.contains(":para:" + this.usuarioAutenticado + ":")) {
                        String[] partes = linea.split(":mensaje:");
                        String mensaje = partes[1];
                        escritor.println(contador + ". " + mensaje);
                        contador++;
                    }
                }
                escritor.println("-------------------------");
            } catch (IOException e) {
                escritor.println("No hay mensajes en tu buzón.");
            }
        }
    }
}