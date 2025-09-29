package com.cliente.com.cliente;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Cliente2025 {

    private static List<String> archivoBuffer = new ArrayList<>();
    private static String archivoRecibidoNombre = null;

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
                            String[] partes = lineaServidor.split(Pattern.quote(":"));
                            String comando = partes[1];
                            String archivo = partes[2];
                            String solicitante = partes[3];
                            
                            if ("LISTAR_ARCHIVOS".equalsIgnoreCase(comando)) {
                                File directorio = new File(".");
                                File[] archivos = directorio.listFiles((dir, nombre) -> nombre.toLowerCase().endsWith(".txt"));
                                
                                escritorServidor.println("_RESPUESTA_LISTAR_ARCHIVOS:" + solicitante);
                                if (archivos != null && archivos.length > 0) {
                                    for (File f : archivos) {
                                        escritorServidor.println(f.getName());
                                    }
                                }
                                escritorServidor.println("_FIN_LISTA_");
                            } 
                            else if ("TRANSFERIR_PREGUNTA".equalsIgnoreCase(comando)) {
                                System.out.println("\n** El usuario '" + solicitante + "' quiere el archivo '" + archivo + "'. **");
                                System.out.print("¿Permites la transferencia? (si/no): ");
                            }
                            else if ("TRANSFERIR_DATOS".equalsIgnoreCase(comando)) {
                                String nombreArchivo = partes[2];
                                String solicitanteOrigen = partes[3];
                                
                                System.out.println("\n--- Iniciando envío de '" + nombreArchivo + "' a " + solicitanteOrigen + " ---");
                                
                                try (BufferedReader br = new BufferedReader(new FileReader(nombreArchivo))) {
                                    String linea;
                                    while ((linea = br.readLine()) != null) {
                                        escritorServidor.println("_DATOS_ARCHIVO:Origen:" + solicitanteOrigen + ":" + linea);
                                    }
                                    escritorServidor.println("_FIN_ARCHIVO_:Origen:" + solicitanteOrigen + ":" + nombreArchivo);
                                    System.out.println("--- Envío de archivo finalizado ---");

                                } catch (FileNotFoundException e) {
                                    System.out.println("ERROR: Archivo '" + nombreArchivo + "' no encontrado en el directorio.");
                                    escritorServidor.println("ERROR:Archivo no encontrado.");
                                }
                            }

                        } 
                        else if (lineaServidor.startsWith("_ARCHIVO_CONTENIDO:")) {
                            String[] partes = lineaServidor.split(":", 2); 
                            archivoBuffer.add(partes[1]);
                        }
                        else if (lineaServidor.startsWith("_ARCHIVO_FINALIZADO:")) {
                            String[] partes = lineaServidor.split(":");
                            archivoRecibidoNombre = partes[1];
                            
                            System.out.println("\n--- ¡Transferencia Completa! ---");
                            System.out.print("¿Deseas guardar el archivo '" + archivoRecibidoNombre + "'? (GUARDAR:nombre.txt / CANCELAR): ");
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
                    escritorServidor.println("_RESPUESTA_PERMISO:" + respuesta + ":" + comando); 
                } 
                else if (comando.startsWith("GUARDAR:")) {
                    String[] partes = comando.split(":");
                    if (partes.length == 2 && archivoRecibidoNombre != null) {
                        String nombreDestino = partes[1].trim();
                        try (FileWriter fw = new FileWriter(nombreDestino)) {
                            for (String linea : archivoBuffer) {
                                fw.write(linea + "\n");
                            }
                            System.out.println("Archivo guardado como: " + nombreDestino);
                            archivoBuffer.clear(); 
                            archivoRecibidoNombre = null;
                            escritorServidor.println("Archivo guardado. Volviendo al menú.");
                        } catch (IOException e) {
                            System.err.println("ERROR: No se pudo guardar el archivo localmente.");
                            escritorServidor.println("Error al guardar archivo. Volviendo al menú.");
                        }
                    } else {
                        System.out.println("Formato incorrecto o no hay archivo para guardar.");
                    }
                } 
                else if ("CANCELAR".equalsIgnoreCase(comando)) {
                    archivoBuffer.clear();
                    archivoRecibidoNombre = null;
                    System.out.println("Guardado cancelado.");
                    escritorServidor.println("Operación de transferencia cancelada. Volviendo al menú.");
                }
                else {
                    escritorServidor.println(comando);
                }
            }
        } catch (IOException e) {
            System.err.println("Error en la conexión o I/O: " + e.getMessage());
        }
    }
}