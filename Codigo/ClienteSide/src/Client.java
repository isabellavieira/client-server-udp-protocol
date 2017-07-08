import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * *
 * Cliente envia um arquivo para o servidor de acordo com as opções de envio
 * escolhidas (com perda, duplicados ou embaralhados)
 *
 * @author monica
 */
public class Client {

	private static final int SOCKETNUM = 9876;
	private ArrayList<DatagramPacket> datagramas = null;
	private boolean flagAlternar = false;

	Socket sender;
	ObjectOutputStream out;
	ObjectInputStream in;
	String packet, ack, str, msg;
	int n, i = 0, sequence = 0;

	public int criarDatagramas(int max, InetAddress IPaddress){
		datagramas = new ArrayList<DatagramPacket>();
		byte[] dados = new byte[1024];

		String mensagemParaEnviar = new String("Testando envio de uma mensagem");
		String cabecalhoPadrao = "";
		String dado = null;
		byte[] sendData = new byte[1024];
		byte[] cabecalho = new byte[1024];
		boolean ack = false;
		sendData = mensagemParaEnviar.getBytes();
		String[] listaDadosPacotes = mensagemParaEnviar.split("(?<=\\G.{5})");
		int id = 0;
		
		System.out.println("Número de datagramas para enviar: " + listaDadosPacotes.length + "\n");
		for(int i=0;i<listaDadosPacotes.length;i++){
				
			//Criando cabeçalho: #pacoteEnvio #pacoteEsperado #numDatagramasTotal ack size
            cabecalhoPadrao = "" +i +"-@-" + -1 + "-@-" + listaDadosPacotes.length + "-@-"+ ack +"-@-" + listaDadosPacotes[i].length()+ "-%-";

			//Concatenando cabeçalho e parte dos dados
			dado = cabecalhoPadrao+listaDadosPacotes[i];

			//Criando datagrama
			dados = dado.getBytes();
			DatagramPacket item = novoDatagrama(IPaddress, dados);
			datagramas.add(item);
		}	

		return (listaDadosPacotes.length);
	}

	/*
	 * Cria um novo datagrama pacote
	 */
	public DatagramPacket novoDatagrama(InetAddress IPaddress, byte[] dados ){		
		DatagramPacket pacote = new DatagramPacket(dados, dados.length, IPaddress, SOCKETNUM);
		return pacote;
	}

	public DatagramPacket getNextDatagrama(int index){
		return (datagramas.get(index));
	}

	public DatagramPacket simulacaoDados(int flag, int nextSequence, int tamanho) {
		int index;
		int timeout = 1;
		Random rand = new Random();

		DatagramPacket pacote = null;

		if (nextSequence == 0 || flag == 0) {
			pacote = getNextDatagrama(nextSequence);
		} else {
			if (flag == 1) {       // perder dados
				if (flagAlternar == true) {
					try {
						TimeUnit.SECONDS.sleep(timeout);
					} catch (InterruptedException e) {
						System.out.println("ERRO slepp");
					}
					pacote = getNextDatagrama(nextSequence);
					flagAlternar = false;
				} else {
					pacote = getNextDatagrama(nextSequence);
					flagAlternar = true;
				}
			} else if (flag == 2) { // duplicar dados
				if (flagAlternar == true) {
					index = nextSequence-1;     // envia dado ja enviado
					pacote = getNextDatagrama(index);
					flagAlternar = false;
				} else {
					flagAlternar = true;
					pacote = getNextDatagrama(nextSequence);
				}
			} else {        // embaralhar dados
				if (flagAlternar == true) {
					int randomNum = rand.nextInt(tamanho);
					pacote = getNextDatagrama(randomNum);
					flagAlternar = false;
				} else {
					index = nextSequence;
					pacote = getNextDatagrama(index);
					flagAlternar = true;
				}
			}
		}
		return pacote;
	}


	public void enviarDatagrama(DatagramSocket clientSocket, DatagramPacket pacote){
		try {
			clientSocket.send(pacote);
		} catch (IOException e) {
			System.out.println("ERRO no envio do pacote\n");
			e.printStackTrace();
		}
	}

	
	public String extrairDados(DatagramPacket pacote, MutableInteger numeroDeSequenciaEsperado){
        String message = new String(pacote.getData()); 
        String[] dados = message.split("-%-");
        
      //Criando cabeçalho: #pacoteEnvio #pacoteEsperado ack size
        String[] cabecalho = dados[0].split("-@-");
        numeroDeSequenciaEsperado.value = Integer.parseInt(cabecalho[1]);
        
		return (dados[1]);
	}
	
	public void receberACK(DatagramSocket clientSocket, MutableInteger indexPacoteEnviado){
		byte[] dadosReceber = new byte[1024];
		DatagramPacket pacoteRecebido = new DatagramPacket(dadosReceber, dadosReceber.length);

		try {
			clientSocket.receive(pacoteRecebido);
			MutableInteger numeroDeSequenciaEsperado = new MutableInteger();
			numeroDeSequenciaEsperado.value = 0;
			
			String message = extrairDados(pacoteRecebido, numeroDeSequenciaEsperado);
			if (indexPacoteEnviado.value >= 10) System.out.println("Esperando ACK"+indexPacoteEnviado.value + "...");
			else System.out.println("Esperando ACK0"+indexPacoteEnviado.value + "...");

			String ack = null;
			
			if (indexPacoteEnviado.value >= 10)ack = "ACK"+indexPacoteEnviado.value;
			else ack = "ACK0"+indexPacoteEnviado.value;
			String[] messageTratada = message.split("(?<=\\G.{5})");
			
			//ACK do pacote enviado foi recebido
			if (messageTratada[0].equals(ack)){
				indexPacoteEnviado.value++;		
				System.out.println("Recebido " + message + "\n");

			//ACK de pacote duplicado
			} else {
				System.out.println("Recebido " + message);
				indexPacoteEnviado.value = numeroDeSequenciaEsperado.value;
			}			
			
		} catch (SocketTimeoutException e) {
			 System.out.println("Timeout atingido! ");			
			 
		} catch (IOException e) {
			System.out.println("ERRO IO ");
		}
	}


	public void run(int flag){
		MutableInteger indexPacoteParaEnviar = new MutableInteger();
		indexPacoteParaEnviar.value = 0;
		int numeroDeDatagramas = 0;
		DatagramSocket clientSocket = null;
		InetAddress IPaddress = null;
				
		try{
			//Socket do cliente
			clientSocket = new DatagramSocket();
			IPaddress = InetAddress.getByName("localhost");

		}catch(Exception e){
			System.out.println("ERRO: criação do socket\n");
			return;
		}
		
		// set the timeout in millisecounds
		try {			
			clientSocket.setSoTimeout(1000);
		} catch (SocketException e) {
			System.out.println("ERRO set timeout");
		}   
		
		numeroDeDatagramas = criarDatagramas(numeroDeDatagramas, IPaddress);

		while (indexPacoteParaEnviar.value < numeroDeDatagramas){
			DatagramPacket pacote;
			pacote = simulacaoDados(flag, indexPacoteParaEnviar.value, numeroDeDatagramas);
			enviarDatagrama(clientSocket, pacote);
			System.out.println("\nEnviado pacote " + indexPacoteParaEnviar.value);
			receberACK(clientSocket, indexPacoteParaEnviar);
		}

		System.out.println("Todos os pacotes foram enviados ");
		clientSocket.close();
	}

	public void printCabecalho(){ 
		System.out.println("-----Menu de opções-----\n");
		System.out.println("[0] Envio normal");
		System.out.println("[1] Perder Dados");
		System.out.println("[2] Duplicar Dados");
		System.out.println("[3] Embaralhar Dados");
		System.out.println("[4] Sair\n");
		System.out.println("Digite sua opção: ");
	}

	public void limparTela () {
		for (int i = 0; i<200; i++) {
			System.out.print("\n");  
		}
	}

	public static void main(String args[]){

		System.out.println(":: Cliente ::\n");
		Client cliente = new Client();
		int flag = 0;
		
		while(true){

			// Imprime menu
			cliente.printCabecalho();

			// Pega info do teclado                        
			BufferedReader infoTeclado = new BufferedReader (new InputStreamReader(System.in));
			String opcao = "";
			try {
				opcao = infoTeclado.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}

			flag = Integer.parseInt(opcao);

			if (flag == 4) {
				System.exit(0);
			} else if ((flag == 1) || (flag == 2) || (flag == 3) || (flag == 0)) {
		
				cliente.run(flag);
		
			} else {
				System.out.println("ERRO! Por favor, digite opções de 1 a 4");
			}

			//cliente.limparTela();                  		
		}
	}
}
