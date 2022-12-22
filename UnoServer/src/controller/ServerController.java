package controller;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import model.RoomModel;
import model.UserModel;

public class ServerController {

    private final int port = 5000;
    private final int portDb = 6000;
    
    private ServerSocket serverSocket = null;
    
    private Connection con = null;       
    
    private ArrayList<ObjectOutputStream> listOos = new ArrayList<>();
    private ArrayList<ObjectInputStream> listOis = new ArrayList<>();
    private ArrayList<RoomModel> listRoom = new ArrayList<>();
    private HashMap<RoomModel, ArrayList<ObjectOutputStream> > listOosRoom  
            = new HashMap<>();

    public void runServer() {      
        runDb();
        runGame();
    }  
    
    public void runDb() {
        connectDb();
        System.out.println("Connect database success.");
        
        Thread threadDb = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSocketDb = new ServerSocket(portDb);
                    
                    while (true) {
                        Socket socketDb = serverSocketDb.accept();
                        
                        ObjectOutputStream oosDb = new ObjectOutputStream(socketDb.getOutputStream());
                        ObjectInputStream oisDb = new ObjectInputStream(socketDb.getInputStream());
                        
                        boolean isRunning = true;
                        while (isRunning) {
                            Object receive = oisDb.readObject();

                            if (receive instanceof String) {
                                String receiveString = (String) receive;
                                
                                if (receiveString.equals("register")) {
                                    UserModel tmp = (UserModel) oisDb.readObject();

                                    if (checkUser(tmp) == 0 || checkUser(tmp) == 1) {
                                        oosDb.writeObject(false);
                                        oosDb.flush();                                
                                    }
                                    else {
                                        oosDb.writeObject(registerUser(tmp));
                                        oosDb.flush();
                                    }    
                                    
                                    isRunning = false;
                                }
                                else if (receiveString.equals("login")) {
                                    UserModel tmp = (UserModel) oisDb.readObject();

                                    oosDb.writeObject(checkUser(tmp));
                                    oosDb.flush();
                       
                                    isRunning = false;
                                }
                            }
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        
        threadDb.start(); 
    }
    
    public void runGame() {
        Thread threadGame = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    System.out.println("Server is running ...");
                                        
                    while (true) {
                        Socket socket = serverSocket.accept();
                        System.out.println("New player joined");
                        
                        Thread tmpServer = new ThreadServer(socket, listOos, 
                                listOis, listRoom, listOosRoom);
                        tmpServer.start();
                    }
                }
                catch (Exception e) {
                    System.out.println(e);
                }
            }
        });
        
        threadGame.start();
    }
    
    public void connectDb() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            String host = "localhost";
            int port = 3306;
            String dbName = "uno";
            String username = "root";
            String pass = "123456789";
            
            String mysqlURL ="jdbc:mysql://" + host + ":" + port + "/" + dbName;     
            con = DriverManager.getConnection(mysqlURL, username, pass);
        }
        catch (Exception e) {
            System.out.println(e);
        }
    }
    
    // 0: that bai do sai info, 1: that bai do dang dang nhap, 2: thanh cong
    public int checkUser(UserModel user) {
        int tmp = 0;
        
        try {
            Statement statement = con.createStatement();
            String query = "SELECT * FROM user WHERE username='" + user.getUsername() 
                + "' AND password='" + user.getPass() + "'";
        
            ResultSet result = statement.executeQuery(query);
            
            if (result.next()) {
                if (result.getInt(4) == 1)
                    tmp = 1;
                else
                    tmp = 2;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println(tmp);
        return tmp;
    }
    
    public boolean registerUser(UserModel user) {
        try {
            Statement statement = con.createStatement();
            String query = "INSERT INTO user VALUES ('" + user.getUsername() 
                + "', '" + user.getPass() + "')";
        
            int result = statement.executeUpdate(query);
            
            if (result != 0)
                return true;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        
        return false;
    }
}