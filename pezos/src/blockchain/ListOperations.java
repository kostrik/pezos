package blockchain;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import tools.Utils;

public class ListOperations {
	private ArrayList<Operation> listOperations = null;
	
	public ListOperations(byte[] receivedMessage) {
		listOperations = new ArrayList<Operation>();
		extractFirstOperation(Arrays.copyOfRange(receivedMessage,4,receivedMessage.length));
	}
	
	public void extractFirstOperation(byte[] operationsAsBytes) {
		if(operationsAsBytes.length<1)
			return;
		
		short tag = Utils.toShort(Arrays.copyOfRange(operationsAsBytes,0,2));
		int nextPosition=0;
		if (tag==1 || tag==3 || tag==4 ) {
			nextPosition=130;
		} else if (tag == 2) {
			nextPosition=106;
		} else if (tag == 5) {
			nextPosition=98;
		} else
			return;
		listOperations.add(new Operation(Arrays.copyOfRange(operationsAsBytes,0,nextPosition)));
		extractFirstOperation(Arrays.copyOfRange(operationsAsBytes,nextPosition,operationsAsBytes.length));
	}
	
	public byte[] getHash() throws IOException {
		return getHash(listOperations);
	}

	public byte[] getHash(ArrayList<Operation> listOperationsLoc) throws IOException {
		if (listOperationsLoc.size() == 0) 
			return Utils.to32bytesArray(0);
		if (listOperationsLoc.size() == 1) 
			return Utils.hash(listOperationsLoc.get(0).getAsBytes(),32);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		byte[] hashLastOperation = Utils.hash(listOperationsLoc.get(listOperationsLoc.size()-1).getAsBytes(),32);
		byte[] hashAllOperationExceptTheLast = getHash(new ArrayList<Operation>(listOperationsLoc.subList(0,listOperationsLoc.size()-1))); 
		outputStream.write(hashAllOperationExceptTheLast);
		outputStream.write(hashLastOperation);
		return Utils.hash(outputStream.toByteArray(),32);
	}

	public String toString() {
		String result = "OPERATIONS : "+listOperations.size()+" operations of the block : \n";
		for(Operation operation : listOperations)
			result += "  "+operation.toString()+"\n";
		return result.substring(0,result.length()-1);
	}
}