package controller;

import java.io.*;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import model.PlayerModel;
import model.RoomModel;
import model.UnoModel;

public class ThreadServer extends Thread {

    private Socket socket;
    private ArrayList<RoomModel> listRoom;
    private ArrayList<ObjectOutputStream> listOos;
    private ArrayList<ObjectInputStream> listOis;
    private HashMap<RoomModel, ArrayList<ObjectOutputStream> > listOosRoom;
    
    private ObjectOutputStream oos = null;
    private ObjectInputStream ois = null;
    
    private Connection con = null; 
        
    public ThreadServer(Socket socket,
                        ArrayList<ObjectOutputStream> listOos,
                        ArrayList<ObjectInputStream> listOis,
                        ArrayList<RoomModel> listRoom,
                        HashMap<RoomModel, ArrayList<ObjectOutputStream> > listOosRoom) {
        this.socket = socket;
        this.listOos = listOos;
        this.listOis = listOis;
        this.listRoom = listRoom;
        this.listOosRoom = listOosRoom;
        
        connectDb();
    }

    @Override
    public void run() {
        try { 
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());
            
            listOos.add(oos);
            listOis.add(ois);

            while (true) {
                Object receive = ois.readObject();
                System.out.println("Receive: " + receive);
                           
                if (receive instanceof String) {
                    String receiveString = (String) receive;
                    
                    if (receiveString.matches("ready:.+")) {
                        String[] tmp = receiveString.split(":", 2);
                        int idRoom = Integer.parseInt(tmp[1]);
                        
                        for (RoomModel i: listRoom)
                            if (i.getId() == idRoom 
                                    && i.getListReadyPlayer().size() == i.getNumRequest()) {
                                i.setUnoModel(new UnoModel());
                                i.getUnoModel().setCurrentPlayerIndex();
                                i.getUnoModel().startGame(i.getListPlayer());
                                i.setIsProgress(true);
                                
                                sendAllClient(listRoom);
                                sendAllInRoom("start game", i);
                               
                                break;
                            }
                    }
                    else if (receiveString.matches("join room:.+")) {
                        String[] tmp = receiveString.split(":", 2);
                        int idRoom = Integer.parseInt(tmp[1]);
                        
                        for (RoomModel i: listOosRoom.keySet())
                            if (i.getId() == idRoom) {
                                ArrayList<ObjectOutputStream> tmpArrayList = listOosRoom.get(i);
                                if (!tmpArrayList.contains(oos))
                                    listOosRoom.get(i).add(oos);
                            }
                    }
                    else if (receiveString.matches("out room:.+")) {
                        String[] tmp = receiveString.split(":", 2);
                        int idRoom = Integer.parseInt(tmp[1]);

                        for (RoomModel i: listOosRoom.keySet())
                            if (i.getId() == idRoom)
                                listOosRoom.get(i).remove(oos);
                    }
                    else if (receiveString.matches("log out:.+")) {
                        listOis.remove(ois);
                        listOos.remove(oos);
                        
                        sendAllClient(listRoom);
                        
                        String name = splitString(receiveString);
                        updateStatusLogin(name, 0);
                    }
                    else if (receiveString.matches("request uno model:.+")) {
                        String tmpNamePlayer = splitString(receiveString);
                        RoomModel tmpRoom = getRoomContainPlayer(tmpNamePlayer);
                        
                        sendToClient(tmpRoom.getUnoModel());
                    }
                    else if (receiveString.matches("end game:.+")) {
                        String tmpNamePlayer = splitString(receiveString);
                        RoomModel tmpRoom = getRoomContainPlayer(tmpNamePlayer);
                        
                        for (RoomModel i: listRoom)
                            if (i.getId() == tmpRoom.getId()) {
                                i.setIsProgress(false);
                                i.setListReadyPlayer(new ArrayList<PlayerModel>());
                                
                                break;
                            }
                        
                        updatePoint(tmpNamePlayer);
                        
                        sendAllInRoom("end game:" + tmpNamePlayer, tmpRoom);
                        sendAllClient(listRoom);
                    }
                    else if (receiveString.equals("get list room")) {
                        sendToClient(listRoom);
                    }
                    else if (receiveString.matches("get rank:.+")) {
                        HashMap<String, Integer> rank = getRank();
                        sendToClient(rank);
                    }
                    else if (receiveString.matches("update login:.+")) {
                        String name = splitString(receiveString);
                        updateStatusLogin(name, 1);
                    }
                    else {
                        String[] tmp = receiveString.split(":", 2);
                        String tmpNamePlayer = tmp[0];
                        RoomModel tmpRoom = getRoomContainPlayer(tmpNamePlayer);
                        
                        sendAllInRoom(receiveString, tmpRoom);
                    }
                }
                else if (receive instanceof RoomModel) {
                    RoomModel receiveRoom = (RoomModel) receive;
                    boolean checkRoom = false;
                    
                    RoomModel tmpRoom = new RoomModel();
                    for (RoomModel i: listRoom)  
                        if (i.getId() == receiveRoom.getId()) {
                            checkRoom = true; 
                            tmpRoom = i;  
                            
                            break;
                        }
                    
                    if (checkRoom) {
                        receiveRoom.setIsProgress(tmpRoom.isIsProgress());
                        if (receiveRoom.getListPlayer().size() == 0)
                            receiveRoom.setIsProgress(false);
                        
                        listRoom.remove(tmpRoom);
                        listRoom.add(receiveRoom);       
                    }
                    else {
                        listRoom.add(receiveRoom);
                        listOosRoom.put(receiveRoom, new ArrayList<>());            
                    }
                   
                    sendAllClient(listRoom);
                }
                else if (receive instanceof UnoModel) { 
                    for (RoomModel i: listRoom)
                        if (i.getId() == getRoomByOos().getId()) {
                            i.setUnoModel((UnoModel) receive);
                            sendAllInRoom((UnoModel) receive, i);
                            
                            break;
                        }
                }        
            }
        } 
        catch (Exception e) {
            try {
                socket.close();
                listOis.remove(ois);
                listOos.remove(oos);
            }
            catch (Exception ex) {
                System.out.println(ex + " ex in catch of main ");
            }
            
            System.out.println(e + " e in catch of main");
        }
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
    
    public HashMap<String, Integer> getRank() {
        HashMap<String, Integer> result = new HashMap<>();
        
        try {
            Statement statement = con.createStatement();

            String query = "SELECT username, point FROM user";
            ResultSet resultQuery = statement.executeQuery(query);
            while (resultQuery.next())
                result.put(resultQuery.getString(1), resultQuery.getInt(2));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
    
    public void updatePoint(String name) {
        try {
            Statement statement = con.createStatement();

            String getPointNow = "SELECT point FROM user WHERE username='" + name + "'";
            ResultSet result = statement.executeQuery(getPointNow);
            int pointBefore = 0;
            if (result.next())
                pointBefore = result.getInt(1);
            
            String updatePoint = "UPDATE user SET point = " + (pointBefore + 100)
                    + " WHERE username = '" + name + "'";       
            int resultUpdate = statement.executeUpdate(updatePoint);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void updateStatusLogin(String name, int status) {
        try {
            Statement statement = con.createStatement();

            String update = "UPDATE user SET isLogin = " + status
                    + " WHERE username = '" + name + "'"; 
            int resultUpdate = statement.executeUpdate(update);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public RoomModel getRoomByOos() {
        for (RoomModel i: listOosRoom.keySet())
            if (listOosRoom.get(i).contains(oos))
                return i;
        
        return null;
    }
    
    public RoomModel getRoomContainPlayer(String namePlayer) {
        for (RoomModel i: listRoom) {
            ArrayList<PlayerModel> tmp = i.getListPlayer();
            
            for (PlayerModel j: tmp)
                if (namePlayer.equals(j.getName()))
                    return i;
        }
        
        return null;
    }
    
    public String splitString(String s) {
        String[] tmp = s.split(":", 2);
        
        return tmp[1];
    }
    
    public void sendAllClient(Object o) {
        for (ObjectOutputStream i: listOos) {
            try {
                i.reset();
                i.writeObject(o);
                i.flush();
            } catch (Exception e) {
                System.out.println(e + " in sendAllClient");
            }
        }
        
        System.out.println("Send to all: " + o);
    }

    public void sendAllInRoom(Object o, RoomModel room) { 
        ArrayList<ObjectOutputStream> tmpArrayOut = new ArrayList();
        
        for (RoomModel i: listOosRoom.keySet())
            if (i.getId() == room.getId())
                tmpArrayOut = listOosRoom.get(i);
        
        for (ObjectOutputStream i: tmpArrayOut) {
            try {
                i.reset();
                i.writeObject(o);
                i.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        System.out.println("Send to room " + room + ": " + o);
    }
    
    public void sendToClient(Object o) {
        try {
            oos.reset();
            oos.writeObject(o);
            oos.flush();
            
            System.out.println("Send to one: " + o);
        }
        catch (Exception e) {
            System.out.println(e + " in sendToClient");
        }
    }
}