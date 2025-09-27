package com.cliente.com.cliente;

import java.io.*;
import java.net.Socket;

public class Cliente2025 {

    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 8080);
             BufferedReader lectorServidor = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter escritorServidor = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in))) {

            Thread lectorThread = new Thread(() -> {
                String lineaServidor;
                try {
                    while ((lineaServidor = lectorServidor.readLine()) != null) {
                        if (lineaServidor.startsWith("_COMANDO_:")) {
                            String[] partes = lineaServidor.split(":");
                            String comando = partes[1];
                            String archivo = partes[2];
                            String solicitante = partes[3];
                            
                            if ("LISTAR_ARCHIVOS".equalsIgnoreCase(comando)) {
                                File directorio = new File(".");
                                File[] archivos = directorio.listFiles((dir, nombre) -> nombre.toLowerCase().endsWith(".txt"));
                                
                                escritorServidor.println("_RESPUESTA_LISTAR_ARCHIVOS:" + solicitante);
                                if (archivos != null && archivos.length > 0) {
                                    for (File file : archivos) {
                                        escritorServidor.println(file.getName());
                                    }
                                }
                                escritorServidor.println("_FIN_LISTA_");

                            } 
                            else if ("TRANSFERIR_PREGUNTA".equalsIgnoreCase(comando)) {
                                System.out.println("\n** El usuario '" + solicitante + "' quiere el archivo '" + archivo + "'. **");
                                System.out.print("¿Permites la transferencia? (si/no): ");
                            }
                            
                        } 
                        else if (lineaServidor.startsWith("_RESPUESTA_PERMISO:")) {
                            System.out.println("Servidor: " + lineaServidor);
                        }
                        else {
                            System.out.println("Servidor: " + lineaServidor);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Conexion perdida con el servidor.");
                }
            });
            lectorThread.start();

            String comando;
            while ((comando = teclado.readLine()) != null) {
                if ("si".equalsIgnoreCase(comando) || "no".equalsIgnoreCase(comando)) {
                    String respuesta = ("si".equalsIgnoreCase(comando) ? "ACEPTAR" : "DENEGAR");
                    escritorServidor.println("_RESPUESTA_PERMISO:" + respuesta); 
                } else {
                    escritorServidor.println(comando);
                }
            }
        } catch (IOException e) {
            System.err.println("Error en la conexión o I/O: " + e.getMessage());
        }
    }
}