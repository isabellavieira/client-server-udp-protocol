import java.io.*;
import java.net.*;
import java.util.ArrayList;

import javax.sound.midi.Receiver;


/***
 * Servidor recebe um arquivo e garante ordem, controle de perda e remoção de pacotes
 * duplicados.
 * 
 * @author monica 
 */

public class Server{
	
	private static final int SOCKETNUM = 9876;
	private DatagramSocket serverSocket = null;
	private InetAddress IPAddress = null;
	private int porta = 0;
	private ArrayList<Integer> duplicados = null;
	private ArrayList<Integer> perdidos = null;
	private ArrayList<Integer> embaralhados = null; 
	private ArrayList<Integer> normais = null; 
	private String recebido = "";
	public String t= "";

	ServerSocket reciever;
	Socket conexao=null;
	ObjectOutputStream bufferOut;
	ObjectInputStream bufferIn;
	String packet,ack,data="";
	int i=0,sequence=0;


	/*
	 * Cria um novo datagrama pacote
	 */
	public DatagramPacket novoDatagrama(InetAddress IPaddress, byte[] dados ){	
		//TODO ack 
		DatagramPacket pacote = new DatagramPacket(dados, dados.length, IPaddress, porta);
		return pacote;
	}
	
	
	public void enviarDatagrama(DatagramSocket serverSocket, DatagramPacket pacote){
		try {
			serverSocket.send(pacote);
		} catch (IOException e) {
			System.out.println("ERRO no envio do pacote\n");
			e.printStackTrace();
		}
	}
	
	public void enviarACK (int indexDoPacoteRecebido){
		int indexDoPacoteEsperado = indexDoPacoteRecebido + 1;
		String dados = null;
		
		if (indexDoPacoteRecebido >= 10) dados = "ACK" + indexDoPacoteRecebido;
		else dados = "ACK0" + indexDoPacoteRecebido;
		boolean ack = true;
		
		//Criando cabeçalho: #pacoteEnvio #pacoteEsperado ack size
        String cabecalhoPadrao = "" + -1 +"-@-" + indexDoPacoteEsperado + "-@-" + ack +"-@-" + dados.length()+ "-%-";

		//Concatenando cabeçalho e parte dos dados
		String dado = cabecalhoPadrao+dados;

		byte[] dadosEnviar = dado.getBytes();
		
		DatagramPacket pacote = novoDatagrama(IPAddress, dadosEnviar);
		enviarDatagrama(serverSocket, pacote);
		System.out.println("Enviado : " + dados + "\n");
	}
	
	public String extrairDados(DatagramPacket pacote, MutableInteger numeroDeSequenciaRecebido, MutableInteger totalDatagramas){
        String message = new String(pacote.getData()); 
        String[] dados = message.split("-%-");
        
      //Criando cabeçalho: #pacoteEnvio #pacoteEsperado #numDatagramasTotal ack size
        String[] cabecalho = dados[0].split("-@-");
        numeroDeSequenciaRecebido.value = Integer.parseInt(cabecalho[0]);
        totalDatagramas.value = Integer.parseInt(cabecalho[2]);
        String aux = dados[1].toString();
        String te[]=aux.split("");
        t = "";
        for(int q=0;q<5;q++){
        	t = t + te[q];
        }
        
        System.out.println("Dados recebidos : " + t);
       
		return (t);
	}	
	
	public int analisarPacote(DatagramPacket pacoteRecebido, Integer numPacoteEsperado, MutableInteger data){
		
		MutableInteger numPacoteRecebido = new MutableInteger();
		MutableInteger totalDatagramas = new MutableInteger();
		
		String message = extrairDados(pacoteRecebido, numPacoteRecebido,totalDatagramas);
		
		System.out.println("Pacote Recebido: "+ numPacoteRecebido.value);
		if (numPacoteRecebido.value == numPacoteEsperado){
			this.recebido = this.recebido.toString() + message.toString() + "";
			enviarACK(numPacoteRecebido.value);
			normais.add(numPacoteEsperado);
			numPacoteEsperado++;
			
		} else if (numPacoteRecebido.value < numPacoteEsperado){
			duplicados.add(numPacoteRecebido.value);
			enviarACK(numPacoteEsperado-1);		
		
		} else {
			embaralhados.add(numPacoteRecebido.value);
			enviarACK(numPacoteEsperado-1);			
		}
	
		return (numPacoteEsperado);
		
	}
	
	public int run(){
	
		MutableInteger data = new MutableInteger ();
		data.nome = "";
		
	     MutableInteger numDatagramas = new MutableInteger();
	     MutableInteger numDatagramaAtual = new MutableInteger();
		try{
			//Recebendo o primeiro pacote
			byte[] dadosReceber = new byte[1024];
			DatagramPacket primeiroPacoteRecebido = new DatagramPacket(dadosReceber, dadosReceber.length);
			
			serverSocket = new DatagramSocket(SOCKETNUM);
			serverSocket.receive(primeiroPacoteRecebido);
			
			// Get port and IP
            IPAddress = primeiroPacoteRecebido.getAddress();
            porta = primeiroPacoteRecebido.getPort();
            System.out.println("Pacote Recebido:  " + numDatagramaAtual.value + "");
            normais.add(numDatagramaAtual.value);        
			
			String message = extrairDados(primeiroPacoteRecebido, numDatagramaAtual, numDatagramas);
	
            enviarACK(0);
			numDatagramaAtual.value++;
			
			this.recebido = new String(this.recebido.toString() + message.toString() + "");
			
			serverSocket.setSoTimeout(1000);   // set the timeout in millisecounds.
			
			while (numDatagramaAtual.value < numDatagramas.value ){
				
				DatagramPacket pacoteRecebido = new DatagramPacket(dadosReceber, dadosReceber.length);
				
				//Esperar por um tempo
				try {					
					serverSocket.receive(pacoteRecebido);
	                System.out.println("Esperado Pacote:  " + numDatagramaAtual.value + "");
	                
	                
				} catch (SocketTimeoutException e) {
	                System.out.println("Timeout atingido! " + numDatagramaAtual.value);
	                perdidos.add(numDatagramaAtual.value); 
	    			enviarACK(numDatagramaAtual.value-1);
	                continue;
	            }
				
				numDatagramaAtual.value = analisarPacote(pacoteRecebido, numDatagramaAtual.value, data);			
			}			
			
			System.out.println("Todos["+numDatagramas.value+ "] os dados foram recebidos = " + this.recebido.toString());
			serverSocket.close();
			
		} catch(Exception e){
			System.out.println("ERRO criação do socket\n");			
		}
		
		
		return (numDatagramas.value);
	}	
	
	
	public static void main(String args[]){
		
		
		//This class obj
		Server servidor = new Server();
		
		while(true){
			System.out.println("\n> Servidor rodando..\n");
			servidor.duplicados = new ArrayList<Integer>();
			servidor.perdidos =  new ArrayList<Integer>();
			servidor.embaralhados =  new ArrayList<Integer>();
			servidor.normais = new ArrayList<Integer>();
			servidor.recebido = "";
			servidor.t = "";
			
			int datagramasRecebidos = servidor.run();

			System.out.println("\nPacotes normais recebidos("+ ((float)(servidor.normais).size()*100.0/(float)datagramasRecebidos)+ "%):");
			for (int i = 0; i < (servidor.normais).size(); i++){
				System.out.print(servidor.normais.get(i) + " ");
			}
			
			System.out.println("\n\nPacotes duplicados recebidos("+ ((float)(servidor.duplicados).size()*100.0/(float)datagramasRecebidos)+ "%):");
			for (int i = 0; i < (servidor.duplicados).size(); i++){
				System.out.print(servidor.duplicados.get(i) + " ");
			}
			
			System.out.println("\n\nPacotes perdidos("+ ((float)(servidor.perdidos).size()*100.0/(float)datagramasRecebidos)+ "%):");
			for (int i = 0; i < (servidor.perdidos).size(); i++){
				System.out.print(servidor.perdidos.get(i) + " ");
			}	
			
			System.out.println("\n\nPacotes embaralhados recebidos("+ ((float)(servidor.embaralhados).size()*100.0/(float)datagramasRecebidos)+ "%):");
			for (int i = 0; i < (servidor.embaralhados).size(); i++){
				System.out.print(servidor.embaralhados.get(i) + " ");
			}		
		}
	}
}
