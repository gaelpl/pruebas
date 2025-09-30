package com.servidor;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Servidor2025 {

    private static final String ARCHIVO_USUARIOS = "usuarios.txt";
    private static Map<String, String> usuarios = cargarUsuarios();
    private static final String ARCHIVO_MENSAJES = "mensajes.txt";
    private static Map<String, PrintWriter> clientesConectados = new HashMap<>();
    private static Map<String, List<String>> usuariosBloqueados = new HashMap<>();    
    private static Map<String, String> transferenciaPendiente = new HashMap<>();
    private static Map<String, String> solicitudListaPendiente = new HashMap<>();

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
            } else if (escritorRemitente != null) {
                escritorRemitente.println(
                        "El usuario '" + destinatario + "' no está conectado. El mensaje se ha guardado en su buzón.");
            }
        }
    }
    
    private static void manejarRespuestaPermiso(String usuarioRespuesta, String accion, String archivoRespuesta) {
        if (transferenciaPendiente.containsKey(usuarioRespuesta)) {
            String datosSolicitud = transferenciaPendiente.get(usuarioRespuesta);
            String[] datos = datosSolicitud.split(":"); 
            String usuarioSolicitante = datos[0];
            String nombreArchivo = datos[1];
            
            PrintWriter escritorSolicitante = clientesConectados.get(usuarioSolicitante);
            PrintWriter escritorOrigen = clientesConectados.get(usuarioRespuesta);
            
            if (escritorSolicitante == null) {
                transferenciaPendiente.remove(usuarioRespuesta);
                return;
            }
            
            if ("ACEPTAR".equalsIgnoreCase(accion)) {
                escritorSolicitante.println("Permiso concedido por '" + usuarioRespuesta + "'. Iniciando envío de datos...");
                
                if (escritorOrigen != null) {
                    escritorOrigen.println("_COMANDO_:TRANSFERIR_DATOS:" + nombreArchivo + ":" + usuarioSolicitante);
                }
                
            } else if ("DENEGAR".equalsIgnoreCase(accion)) {
                escritorSolicitante.println("El usuario '" + usuarioRespuesta + "' denegó la transferencia del archivo " + nombreArchivo + ".");
            }
            
            transferenciaPendiente.remove(usuarioRespuesta);
        }
    }
    
    private static void reenviarDatos(String lineaCompleta) {
        String[] partes = lineaCompleta.split(Pattern.quote(":")); 
        
        if (partes.length < 5 || !partes[1].equals("DATOS_ARCHIVO")) {
             return; 
        }

        String solicitante = partes[3];
        String datos = partes[4]; 

        PrintWriter escritorSolicitante = clientesConectados.get(solicitante);
        if (escritorSolicitante != null) {
            escritorSolicitante.println("_ARCHIVO_CONTENIDO:" + datos);
        }
    }
    
    private static void manejarFinTransferencia(String lineaCompleta) {
        String[] partes = lineaCompleta.split(Pattern.quote(":")); 
        String solicitante = partes[3];
        String nombreArchivo = partes[4]; 

        PrintWriter escritorSolicitante = clientesConectados.get(solicitante);
        if (escritorSolicitante != null) {
             escritorSolicitante.println("_ARCHIVO_FINALIZADO:" + nombreArchivo + ":Escribe 'GUARDAR:" + nombreArchivo + "' para guardarlo.");
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
                    escritor.println(
                            "Escribe 'jugar', 'chat', 'buzon', 'borrar', 'usuarios', 'eliminar', 'bloquear', 'transferir', 'listar' o 'cerrar'.");
                    String opcion;
                    while ((opcion = lector.readLine()) != null) {
                        
                        if (opcion.startsWith("_RESPUESTA_PERMISO:")) {
                            String[] partes = opcion.split(":"); 
                            manejarRespuestaPermiso(this.usuarioAutenticado, partes[1], partes[2]);
                            continue;
                        }                       
                        if (opcion.startsWith("_DATOS_ARCHIVO:")) {
                            reenviarDatos(opcion);
                            continue;
                        }
                        if (opcion.startsWith("_FIN_ARCHIVO_:")) {
                            manejarFinTransferencia(opcion);
                            continue;
                        }
                        if (opcion.startsWith("GUARDAR:")) {
                            escritor.println("Archivo guardado. Volviendo al menú."); 
                            continue;
                        }
                        
                        if (opcion.startsWith("_RESPUESTA_LISTAR_ARCHIVOS:")) {
                            String[] partes = opcion.split(":"); 
                            String solicitante = partes[2];
                            String usuarioOrigen = this.usuarioAutenticado;

                            if (solicitudListaPendiente.containsKey(usuarioOrigen) && solicitudListaPendiente.get(usuarioOrigen).equals(solicitante)) {
                                PrintWriter escritorSolicitante = clientesConectados.get(solicitante);
                                if (escritorSolicitante != null) {
                                    escritorSolicitante.println("--- Archivos de '" + usuarioOrigen + "' ---");
                                }
                            }
                            continue;
                        }
                        if (solicitudListaPendiente.containsKey(this.usuarioAutenticado) && !opcion.equals("_FIN_LISTA_")) {
                            String solicitante = solicitudListaPendiente.get(this.usuarioAutenticado);
                            PrintWriter escritorSolicitante = clientesConectados.get(solicitante);
                            if (escritorSolicitante != null) {
                                escritorSolicitante.println(opcion); 
                            }
                            continue;
                        }
                        if (opcion.equals("_FIN_LISTA_")) {
                            if (solicitudListaPendiente.containsKey(this.usuarioAutenticado)) {
                                String solicitante = solicitudListaPendiente.get(this.usuarioAutenticado);
                                PrintWriter escritorSolicitante = clientesConectados.get(solicitante);
                                if (escritorSolicitante != null) {
                                    escritorSolicitante.println("-------------------------");
                                }
                                solicitudListaPendiente.remove(this.usuarioAutenticado);
                            }
                            continue;
                        }
                        if ("jugar".equalsIgnoreCase(opcion)) {
                            jugarJuego();
                        } else if ("chat".equalsIgnoreCase(opcion)) {
                            manejarChat();
                        } else if ("buzon".equalsIgnoreCase(opcion)) {
                            cargarBuzon();
                        } else if ("borrar".equalsIgnoreCase(opcion)) {
                            borrarMensaje();
                        } else if ("usuarios".equalsIgnoreCase(opcion)) {
                            mostrarUsuarios();
                        } else if ("eliminar".equalsIgnoreCase(opcion)) {
                            eliminarCuenta();
                            break;
                        } else if ("bloquear".equalsIgnoreCase(opcion)) {
                            manejarBloqueo();
                        } else if ("transferir".equalsIgnoreCase(opcion)) {
                            manejarTransferencia(); 
                        } else if ("listar".equalsIgnoreCase(opcion)) {
                            manejarListarArchivos();
                        } else if ("cerrar".equalsIgnoreCase(opcion)) {
                            cerrarSesion();
                            break;
                        } else {
                            escritor.println(
                                    "Opcion no reconocida. Escribe 'jugar', 'chat', 'buzon', 'borrar', 'usuarios', 'eliminar', 'bloquear', 'transferir', 'listar' o 'cerrar'.");
                        }
                        escritor.println(
                                "Escribe 'jugar', 'chat', 'buzon', 'borrar', 'usuarios', 'eliminar', 'bloquear', 'transferir', 'listar' o 'cerrar'.");
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
                    if (lector != null)
                        lector.close();
                    if (escritor != null)
                        escritor.close();
                    if (cliente != null)
                        cliente.close();
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
                        escritor.println(
                                "¡Correcto! Adivinaste el numero " + numeroSecreto + " en " + intentos + " intentos.");
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

            if (usuariosBloqueados.containsKey(destinatario) && usuariosBloqueados.get(destinatario).contains(this.usuarioAutenticado)) {
                escritor.println("No puedes enviar mensajes a este usuario. Te ha bloqueado.");
                return;
            }
            if (usuariosBloqueados.containsKey(this.usuarioAutenticado) && usuariosBloqueados.get(this.usuarioAutenticado).contains(destinatario)) {
                escritor.println("No puedes enviar mensajes a un usuario que has bloqueado.");
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
            escritor.println(
                    "ADVERTENCIA: Vas a eliminar tu cuenta. Todos tus mensajes enviados serán borrados. Escribe 'confirmar' para continuar.");
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

        private void cerrarSesion() {
            escritor.println("Sesión cerrada. Puedes volver a iniciar sesión.");
            this.usuarioAutenticado = null;
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

        private void mostrarUsuarios() {
            escritor.println("Usuarios existentes:");
            for (String usuario : usuarios.keySet()) {
                escritor.println(usuario);
            }
        }

        private void manejarBloqueo() throws IOException {
            escritor.println("Escribe el nombre de usuario que quieres bloquear o 'salir' para cancelar.");
            String usuarioABloquear = lector.readLine();
            if ("salir".equalsIgnoreCase(usuarioABloquear)) {
                return;
            }
            if (!usuarios.containsKey(usuarioABloquear)) {
                escritor.println("El usuario no existe.");
                return;
            }
            if (usuarioABloquear.equalsIgnoreCase(this.usuarioAutenticado)) {
                escritor.println("No puedes bloquearte a ti mismo.");
                return;
            }
            if (!usuariosBloqueados.containsKey(this.usuarioAutenticado)) {
                usuariosBloqueados.put(this.usuarioAutenticado, new ArrayList<>());
            }
            if (usuariosBloqueados.get(this.usuarioAutenticado).contains(usuarioABloquear)) {
                escritor.println("El usuario " + usuarioABloquear + " ya está en tu lista de bloqueados.");
            } else {
                usuariosBloqueados.get(this.usuarioAutenticado).add(usuarioABloquear);
                escritor.println("Usuario " + usuarioABloquear + " bloqueado exitosamente.");
            }
        }

        private void manejarTransferencia() throws IOException {
            escritor.println("Escribe el usuario y nombre del archivo (Ej: usuarioB:archivo.txt) o 'salir'.");
            String peticion = lector.readLine();
            if ("salir".equalsIgnoreCase(peticion)) return;

            if (peticion.contains(":")) {
                String[] partes = peticion.split(":");
                String usuarioOrigen = partes[0].trim();
                String nombreArchivo = partes[1].trim();

                synchronized (clientesConectados) {
                    PrintWriter escritorOrigen = clientesConectados.get(usuarioOrigen);
                    PrintWriter escritorRemitente = clientesConectados.get(this.usuarioAutenticado);

                    if (escritorOrigen != null) {
                        transferenciaPendiente.put(usuarioOrigen, this.usuarioAutenticado + ":" + nombreArchivo);
                        
                        escritorOrigen.println("_COMANDO_:TRANSFERIR_PREGUNTA:" + nombreArchivo + ":" + this.usuarioAutenticado);
                        escritorRemitente.println("Solicitud de permiso enviada a '" + usuarioOrigen + "'. Esperando respuesta...");
                    } else {
                        escritorRemitente.println("El usuario '" + usuarioOrigen + "' no está conectado. Transferencia cancelada.");
                    }
                }
            } else {
                escritor.println("Formato incorrecto. Usa 'usuario:archivo.txt'.");
            }
        }
        
        private void manejarListarArchivos() throws IOException {
            escritor.println("Escribe el usuario al que deseas listar los archivos o 'salir'.");
            String usuarioObjetivo = lector.readLine();
            if ("salir".equalsIgnoreCase(usuarioObjetivo)) return;
            
            if (!usuarios.containsKey(usuarioObjetivo)) {
                escritor.println("El usuario no existe.");
                return;
            }

            synchronized (clientesConectados) {
                PrintWriter escritorObjetivo = clientesConectados.get(usuarioObjetivo);
                PrintWriter escritorRemitente = clientesConectados.get(this.usuarioAutenticado);

                if (escritorObjetivo != null) {
                    solicitudListaPendiente.put(usuarioObjetivo, this.usuarioAutenticado);                   
                    escritorObjetivo.println("_COMANDO_:LISTAR_ARCHIVOS:" + this.usuarioAutenticado); 
                    escritorRemitente.println("Solicitud de listado enviada a '" + usuarioObjetivo + "'. Esperando respuesta...");
                } else {
                    escritorRemitente.println("El usuario '" + usuarioObjetivo + "' no está conectado. Listado cancelado.");
                }
            }
        }
    }
}