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
    
}