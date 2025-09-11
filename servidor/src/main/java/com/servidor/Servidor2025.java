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
    }
}