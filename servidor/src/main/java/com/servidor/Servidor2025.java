package com.servidor;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Servidor2025 {

    private static final String ARCHIVO_USUARIOS = "usuarios.txt";
    private static Map<String, String> usuarios = cargarUsuarios();

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

    static class ManejadorCliente implements Runnable {
        private Socket cliente;
        private PrintWriter escritor;
        private BufferedReader lector;

        public ManejadorCliente(Socket cliente) {
            this.cliente = cliente;
        }

        public void run() {
            try {
                escritor = new PrintWriter(cliente.getOutputStream(), true);
                lector = new BufferedReader(new InputStreamReader(cliente.getInputStream()));

                boolean autenticado = false;
                while (!autenticado) {
                    escritor.println("Bienvenido. Escribe 'login' para iniciar sesión o 'register' para crear cuenta.");
                    String accion = lector.readLine();

                    if (accion == null) {
                        break;
                    }                    
                    if ("login".equalsIgnoreCase(accion)) {
                        autenticado = manejarLogin();
                    } else if ("register".equalsIgnoreCase(accion)) {
                        manejarRegistro();
                    } else {
                        escritor.println("Acción no reconocida. Intenta de nuevo.");
                    }
                }
            } catch (IOException e) {
                System.err.println("Error en la comunicación con el cliente: " + e.getMessage());
            } finally {
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
            escritor.println("Introduce tu contraseña:");
            String contrasena = lector.readLine();

            if (usuarios.containsKey(usuario) && usuarios.get(usuario).equals(contrasena)) {
                escritor.println("Inicio de sesión exitoso. ¡Bienvenido " + usuario + "!");
                return true;
            } else {
                escritor.println("Usuario o contraseña incorrectos.");
                return false;
            }
        }

        private void manejarRegistro() throws IOException {
            escritor.println("Elige un usuario:");
            String nuevoUsuario = lector.readLine();
            if (usuarios.containsKey(nuevoUsuario)) {
                escritor.println("El usuario ya existe. Intenta iniciar sesión.");
            } else {
                escritor.println("Elige una contraseña:");
                String nuevaContrasena = lector.readLine();
                usuarios.put(nuevoUsuario, nuevaContrasena);
                guardarUsuarios();
                escritor.println("Registro exitoso. Ahora puedes iniciar sesión.");
            }
        }
    }
}