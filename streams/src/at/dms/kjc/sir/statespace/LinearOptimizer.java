package at.dms.kjc.sir.statespace;

/* The optimizer has many subroutines. 
 * 
 *
 *
 */

public class LinearOptimizer {

    FilterMatrix totalMatrix, totalPreMatrix, D;
    FilterVector initVec;
    int states, outputs, inputs, pre_inputs;
    int storedInputs;
    boolean preNeeded;

    /* extracts totalMatrix, totalPreMatrix, and other parameters from linear rep orig

       totalMatrix = | A  B |     totalPreMatrix = | preA  preB |
                     | C  0 |

    */
    public LinearOptimizer(LinearFilterRepresentation orig) {
	
	states = orig.getStateCount();
	outputs = orig.getPushCount();
	inputs = orig.getPopCount();
	preNeeded = orig.preworkNeeded();

	totalMatrix = new FilterMatrix(states+outputs,states+inputs);
	totalMatrix.copyAt(0,0,orig.getA());
	totalMatrix.copyAt(0,states,orig.getB());
	totalMatrix.copyAt(states,0,orig.getC());

	D = orig.getD();

	initVec = orig.getInit();
	storedInputs = orig.getStoredInputCount();

	if(preNeeded) {
	    pre_inputs = orig.getPreWorkPopCount();
	    totalPreMatrix = new FilterMatrix(states,states+pre_inputs);

	    totalPreMatrix.copyAt(0,0,orig.getPreWorkA());
	    totalPreMatrix.copyAt(0,states,orig.getPreWorkB());
	}
    }


    // main function that does all the optimizations
    public LinearFilterRepresentation optimize() {

	int s1, s2;

	// remove unobservable states
	transposeSystem();
	s1 = reduceParameters(false);
	transposeSystem();	
	LinearPrinter.println("got reduction up to value " + s1);	
		
	if(s1 >= 0) {
	    // for now, leave at least 1 state
	    s1 = Math.min(s1,states-1);
	    for(int i=0; i<=s1; i++)
		removeState(0);
	}
	
	// remove unreachable states
	s2 = reduceParameters(true);
	LinearPrinter.println("got reduction up to value " + s2);
			
	if(s2 >= 0) {
	    qr_Algorithm(s2);
	    cleanAll();
	    zeroInitEntries();
	    cleanAll();
	    removeStates(s2);
	} 
	
	return extractRep();
    }


    // extract representation from matrices
    private LinearFilterRepresentation extractRep() {

	LinearFilterRepresentation newRep;
	FilterMatrix A, B, C, preA, preB;

	A = new FilterMatrix(states,states);
	B = new FilterMatrix(states,inputs);
	C = new FilterMatrix(outputs,states);

	A.copyRowsAndColsAt(0,0,totalMatrix,0,0,states,states);
	B.copyRowsAndColsAt(0,0,totalMatrix,0,states,states,inputs);
	C.copyRowsAndColsAt(0,0,totalMatrix,states,0,outputs,states);

	if(preNeeded) {

	    preA = new FilterMatrix(states,states);
	    preB = new FilterMatrix(states,pre_inputs);

	    preA.copyRowsAndColsAt(0,0,totalPreMatrix,0,0,states,states);
	    preB.copyRowsAndColsAt(0,0,totalPreMatrix,0,states,states,pre_inputs);

	    newRep = new LinearFilterRepresentation(A,B,C,D,preA,preB,storedInputs,initVec);
	}
	else
	    newRep = new LinearFilterRepresentation(A,B,C,D,storedInputs,initVec);
	

	return newRep;
    }


    // adds the constant state with value 1 at the end
    private void addConstantState() {

	int newStates = states+1;
	FilterMatrix newTotalMatrix;
	FilterVector newInitVec;

	newTotalMatrix = new FilterMatrix(newStates+outputs,newStates+inputs);
	newTotalMatrix.copyRowsAndColsAt(0,0,totalMatrix,0,0,states,states);
	newTotalMatrix.copyRowsAndColsAt(0,newStates,totalMatrix,0,states,states,inputs);
	newTotalMatrix.copyRowsAndColsAt(newStates,0,totalMatrix,states,0,outputs,states);
	newTotalMatrix.setElement(states,states,ComplexNumber.ONE);
	totalMatrix = newTotalMatrix;

	newInitVec = new FilterVector(newStates);
	newInitVec.copyAt(0,0,initVec);
	newInitVec.setElement(states,ComplexNumber.ONE);
	initVec = newInitVec;


	if(preNeeded) {
	    FilterMatrix newTotalPreMatrix;
	    newTotalPreMatrix = new FilterMatrix(newStates,newStates+pre_inputs);

	    newTotalPreMatrix.copyRowsAndColsAt(0,0,totalPreMatrix,0,0,states,states);
	    newTotalPreMatrix.copyRowsAndColsAt(0,newStates,totalPreMatrix,0,states,states,pre_inputs);
	    newTotalPreMatrix.setElement(states,states,ComplexNumber.ONE);
	    totalPreMatrix = newTotalPreMatrix;
	}

	states = newStates;
    }


    
    // add constant state 1
    // makes all other init vector entries zero    
    private void zeroInitEntries() {
	addConstantState();
	
	double temp;
	
	for(int i=0; i<states-1;i++) {
	    temp = initVec.getElement(i).getReal();
	    if(temp != 0.0) {		
		addMultiple(states-1,i,-temp,true);
	    }
	}		
    }

    
    // rounds all entries which are very close to integral values
    private void cleanAll() {
	totalMatrix.cleanEntries();
	initVec.cleanEntries();
	if(preNeeded)
	    totalPreMatrix.cleanEntries();
    }


    // removes all possible states in the range 0..end_index
    private void removeStates(int end_index) {

	int temp_index = end_index;

	for(int i=end_index; i>=0; i--) {
	    
	    if(isRemovable(i,temp_index)) {
		removeState(i);
		temp_index--;
	    }
	}
    }


    // checks whether or not state index is removable 
    private boolean isRemovable(int index, int end_index) {

	// first check that the state has initial value 0
	if(!initVec.getElement(index).equals(ComplexNumber.ZERO))
	    return false;

	// check that state doesn't get updated by last state
	if(!totalMatrix.getElement(index,states-1).equals(ComplexNumber.ZERO))
	    return false;

	// check that state doesn't get updated by a later state
	// we already know each state doesn't get updated by states greater than end_index, and by earlier states
	for(int i=index+1; i<=end_index; i++) {
	    if(!totalMatrix.getElement(index,i).equals(ComplexNumber.ZERO))
		return false;
	}
	
	if(preNeeded) {
	    //check that state doesn't get initialized by inputs
	    for(int j=0; j<pre_inputs; j++) {
		if(!totalPreMatrix.getElement(index,states+j).equals(ComplexNumber.ZERO))
		    return false;
	    }
	}
		
	// state passes all the tests, so it is removable
	return true;
    }


    // removes state index
    private void removeState(int index) {

	int newStates = states-1;

	FilterMatrix newTotalMatrix = new FilterMatrix(newStates+outputs,newStates+inputs);	
	int lastCols = states+inputs-(index+1);
	int lastRows = states+outputs-(index+1);

        newTotalMatrix.copyRowsAndColsAt(0,0,totalMatrix,0,0,index,index);
	newTotalMatrix.copyRowsAndColsAt(0,index,totalMatrix,0,index+1,index,lastCols);
	
	newTotalMatrix.copyRowsAndColsAt(index,0,totalMatrix,index+1,0,lastRows,index);
	newTotalMatrix.copyRowsAndColsAt(index,index,totalMatrix,index+1,index+1,lastRows,lastCols);
	
	totalMatrix = newTotalMatrix;

	if(preNeeded) {
	    FilterMatrix newPreMatrix = new FilterMatrix(newStates,newStates+pre_inputs);
	    int lastPreCols = states+pre_inputs-(index+1);
	    int lastPreRows = states-(index+1);

	    newPreMatrix.copyRowsAndColsAt(0,0,totalPreMatrix,0,0,index,index);
	    newPreMatrix.copyRowsAndColsAt(0,index,totalPreMatrix,0,index+1,index,lastPreCols);

	    if(index < states-1) {
		newPreMatrix.copyRowsAndColsAt(index,0,totalPreMatrix,index+1,0,lastPreRows,index);
		newPreMatrix.copyRowsAndColsAt(index,index,totalPreMatrix,index+1,index+1,lastPreRows,lastPreCols);
	    }
	    totalPreMatrix = newPreMatrix;
	}

	FilterVector newInitVec = new FilterVector(newStates);

	newInitVec.copyColumnsAt(0,initVec,0,index);

	if(index < states-1)
	    newInitVec.copyColumnsAt(index,initVec,index+1,states-(index+1));
	
	initVec = newInitVec;

	states = newStates;
	
	LinearPrinter.println("Removed state ");
    }


    // does the qr algorithm on A[0..end_index, 0..end_index]
    // also does the appropriate transform to matrix C
    private void qr_Algorithm(int end_index) {

	off_diagonalize(end_index);

	int total_entries = end_index + 1;

	FilterMatrix blockA = new FilterMatrix(total_entries, total_entries);
	blockA.copyRowsAndColsAt(0,0,totalMatrix,0,0,total_entries,total_entries);

	FilterMatrix A12 = null;
	FilterMatrix A21 = null;
	int remain_entries = states-total_entries;
	if(remain_entries > 0) {
	    A12 = new FilterMatrix(total_entries,remain_entries);
	    A21 = new FilterMatrix(remain_entries,total_entries);
	    A12.copyRowsAndColsAt(0,0,totalMatrix,0,total_entries,total_entries,remain_entries);
	    A21.copyRowsAndColsAt(0,0,totalMatrix,total_entries,0,remain_entries,total_entries);
	}

	FilterMatrix blockC = new FilterMatrix(outputs, total_entries);
	blockC.copyRowsAndColsAt(0,0,totalMatrix,states,0,outputs,total_entries);

	FilterMatrix currInit = new FilterMatrix(total_entries,1);

	FilterMatrix blockPreA, blockPreB;
	blockPreA = null;
	blockPreB = null;
	if(preNeeded) {
	    blockPreA = new FilterMatrix(total_entries, total_entries);
	    blockPreB = new FilterMatrix(total_entries, pre_inputs);
	    blockPreA.copyRowsAndColsAt(0,0,totalPreMatrix,0,0,total_entries,total_entries);
	    blockPreB.copyRowsAndColsAt(0,0,totalPreMatrix,0,states,total_entries,pre_inputs);
	}

	for(int i=0; i<total_entries;i++)
	    currInit.setElement(i,0,initVec.getElement(i));

	FilterMatrix QR, Q, Q_trans, R;

	Q = new FilterMatrix(total_entries,total_entries);
	Q_trans = new FilterMatrix(total_entries,total_entries);
        R = new FilterMatrix(total_entries,total_entries);

	//int total = 1000*total_entries*total_entries*total_entries;
	int total = 1000*total_entries;

	LinearPrinter.println("total_entries, all states " + total_entries + " " + states);
	LinearPrinter.println("total iterations: " + total);

	for(int i=0; i<total; i++) {
	  QR = blockA.getQR();
	  Q.copyColumnsAt(0,QR,0,total_entries);
	  Q_trans = Q.transpose();
	  R.copyColumnsAt(0,QR,total_entries,total_entries);

	  /*
	  LinearPrinter.println("Block A: \n" + blockA);
	  LinearPrinter.println("Q*R: \n" + Q.times(R));
	  LinearPrinter.println("Q*Q': \n" + Q.times(Q_trans));
	  LinearPrinter.println("Q, R: \n" + Q + "\n" + R);
	  */
	  blockA = R.times(Q);

	  if(remain_entries > 0) {
	      A12 = Q_trans.times(A12);
	      A21 = A21.times(Q);
	  }

	  blockC = blockC.times(Q);
	  currInit = Q_trans.times(currInit);

	  if(preNeeded) {
	      blockPreA = Q_trans.times(blockPreA.times(Q));
	      blockPreB = Q_trans.times(blockPreB);
	  }

	}

	totalMatrix.copyRowsAndColsAt(0,0,blockA,0,0,total_entries,total_entries);
	totalMatrix.copyRowsAndColsAt(states,0,blockC,0,0,outputs,total_entries);

	if(remain_entries > 0) {
	    totalMatrix.copyRowsAndColsAt(0,total_entries,A12,0,0,total_entries,remain_entries);
	    totalMatrix.copyRowsAndColsAt(total_entries,0,A21,0,0,remain_entries,total_entries);
	}

	if(preNeeded) {
	    totalPreMatrix.copyRowsAndColsAt(0,0,blockPreA,0,0,total_entries,total_entries);
	    totalPreMatrix.copyRowsAndColsAt(0,states,blockPreB,0,0,total_entries,pre_inputs);
	}

	for(int i=0; i<total_entries; i++)
	    initVec.setElement(i,currInit.getElement(i,0));
    }


    // eliminates all zeros below the "off-diagonal" in A[0..end_index, 0..index]
    // the off-diagonal is the diagonal below the main diagonal
    private void off_diagonalize(int end_index) {

	int j = 0;
	double curr, temp, val;
	boolean found = false;

	for(int i=0; i<end_index-1; i++) {

	    curr = totalMatrix.getElement(i+1,i).getReal();
	    if(curr == 0.0) {
		j = i+2;
		found = false;
		while((!found)&&(j<end_index)) {
		    temp = totalMatrix.getElement(j,i).getReal();
		    if(temp != 0.0) {
			swap(j,i+1);
			found = true;
		    }
		    j++;
		}
	    }
	    else {
		found = true;
	    }
		
	    if(found) {
		curr = totalMatrix.getElement(i+1,i).getReal();

		for(int k=i+2; k<end_index; k++) {

		    temp = totalMatrix.getElement(k,i).getReal();
		    if(temp != 0.0) {
			val = -temp/curr;
			addMultiple(i+1,k,val,true);
		    }
		}
		
	    }
	}
    }



    // eliminates entries in totalMatrix, totalPreMatrix
    // returns an int s indicating that states 0..s do not depend on inputs
    // boolean normal indicates whether or not we are using the normal matrices (as opposed to transposes)
    private int reduceParameters(boolean normal) {

	int i = states-1;
	int j = states+inputs-1;
	int r;
	boolean found;

	int max_index; double max_val, temp_val;

	while(i > 0) {
	    
	    found = false;

	    while((j>i)&&(!found)) {
		int k=i;

		while((k >= 0)&&(!found)) {
		    if(!totalMatrix.getElement(k,j).equals(ComplexNumber.ZERO))
			found = true;
		    k--;
		}

		if(!found)
		    j = j-1;
	    }

	    if(i == j)
		break;

	    LinearPrinter.println("hello " + totalMatrix.getElement(i,j));

	    /*
	    if(totalMatrix.getElement(i,j).equals(ComplexNumber.ZERO)) { 

		LinearPrinter.println("here " + i + " " + j);
		r = i-1;
		while((r >= 0)&&(totalMatrix.getElement(r,j).equals(ComplexNumber.ZERO)))
		    r--;
	    
		if(r >= 0) 
		    swap(r,i);
		
	    }
	    */


	    //partial pivoting
	    max_index = i;
	    max_val = Math.abs(totalMatrix.getElement(i,j).getReal());
	    for(int l=0; l<i; l++) {
		temp_val = Math.abs(totalMatrix.getElement(l,j).getReal());
		if(temp_val > max_val) {
		    max_val = temp_val;
		    max_index=l;
		}
	    }
	    swap(max_index,i);

	    double curr = totalMatrix.getElement(i,j).getReal();
	    
	    LinearPrinter.println("i,j,curr " + i + " " + j + " " + curr);

	    if(curr != 0.0) {
		//scale(i,1/curr);
		for(int k=0; k<i; k++) {
		    if((!totalMatrix.getElement(k,j).equals(ComplexNumber.ZERO))) {
			double temp = totalMatrix.getElement(k,j).getReal();
			double val = -temp/curr;
			
			addMultiple(i,k,val,normal);					       
		    }
		}
	    }

	    i = i - 1; 
	    j = j - 1; 
	}

	if(i == j)
	    return i;

	else
	    return -1;
    }


    // swaps rows a,b and cols a,b in totalMatrix, totalPreMatrix
    // also swaps elements a,b in init vector
    private void swap(int a, int b) {

	totalMatrix.swapRowsAndCols(a,b);
	if(preNeeded) {
	    totalPreMatrix.swapRowsAndCols(a,b);
	}
	initVec.swapCols(a,b);
	
	LinearPrinter.println("SWAPPED " + a + " " + b);
    }


    // adds val*row a to row b, adds -val*col b to col a in totalMatrix, totalPreMatrix
    // also adds val*element a to element b in init vector
    private void addMultiple(int a, int b, double val, boolean normal) {

	totalMatrix.addRowAndCol(a,b,val);
	if(preNeeded) {
	    totalPreMatrix.addRowAndCol(a,b,val);
	}

	if(normal)
	    initVec.addCol(a,b,val);
	else
	    initVec.addCol(b,a,-val);

	LinearPrinter.println("ADDED MULTIPLE " + a + " " + b + " " + val);			
    }


    // multiplies row a, col a by val in totalMatrix, totalPreMatrix
    // also multiplies element a by val in init vector
    private void scale(int a, double val) {

	totalMatrix.multiplyRowAndCol(a,val);
	if(preNeeded) {
	    totalPreMatrix.multiplyRowAndCol(a,val);			    
	}
	initVec.multiplyCol(a,val);

	LinearPrinter.println("SCALED " + a + " " + val);		

    }


    //transpose the matrices, and swap the values of inputs, outputs
    private void transposeSystem() {
	
	totalMatrix = totalMatrix.transpose();
	if(preNeeded)
	    totalPreMatrix = totalPreMatrix.transpose();

	int temp = outputs;
	outputs = inputs;
	inputs = temp;
    }

}