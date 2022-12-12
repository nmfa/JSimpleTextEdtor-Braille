package com.anas.jsimpletexteditor;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.logging.Logger;
import javax.swing.JTextArea;


class KeyData {
    public char keyChar;
    public int keyCode;

    KeyData(char ch) {
        keyChar = ch;
        keyCode = (int)Character.toUpperCase(keyChar);
    }

	KeyData(char ch, int co) {
		keyChar = ch;
		keyCode = co;
	}
}




public class TextAreaBraille extends JTextArea {
	// pinCode, KeyData
    private HashMap<Integer, KeyData> brailleMap = new HashMap<Integer, KeyData>();
	// pinCode, KeyData
    private HashMap<Integer, KeyData> numberMap = new HashMap<Integer, KeyData>();
	// keyCode, pinCode bit
    private HashMap<Integer, Integer> keyPinMap = new HashMap<Integer, Integer>();
    Logger log = Logger.getLogger("TextAreaBraille");

    private int currentPinCode = 0;
    private boolean lastKeyDown = false;
	private int shift = 0; // 0 = no shift, 1 = normal, 2 = word, 3 = Caps Lock
	private int digit = 0; // 0 = not a digit, 1 = normal, 2 = word, 3 = Num Lock

    TextAreaBraille() {
        super();
        populateBrailleMaps();
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
		Integer pin = keyPinMap.get(e.getKeyCode());
        boolean keyDown;
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            keyDown = true;
			//log.info("KEYDOWN: " + e.getKeyChar() + ", " + e.getKeyCode());

			// Ignore for keyDown
			switch (e.getKeyCode()) {
				case KeyEvent.VK_ENTER:
				case KeyEvent.VK_SPACE:
					return;
			}
        } else if (e.getID() == KeyEvent.KEY_RELEASED) {
            keyDown = false;
			//log.info("KEYUP: " + e.getKeyChar() + ", " + e.getKeyCode());

			// Pass through. Reset shift below Caps Lock (3) for white space.
			int keyCode = e.getKeyCode();
			switch (keyCode) {
				case KeyEvent.VK_ENTER:
				case KeyEvent.VK_SPACE:
					sendKeyEvents(e.getComponent(), e. getWhen(), -keyCode);
					if (shift < 3) shift = 0;
					if (digit < 3) digit = 0;
			}

			// Getting engaged when it's the last key left of a larger combination, so only after a keyDown
			if (lastKeyDown) {
				// Shift
				if (currentPinCode == SHIFT) {
					shift++;
					if (shift > 3) shift = 0;
					if (digit == 1) digit = 0;
					currentPinCode = 0;
					return;
				}
				// Number
				if (currentPinCode == DIGIT) {
					digit++;
					if (digit > 3) digit = 0;
					if (shift == 1) shift = 0;
					currentPinCode = ~(~currentPinCode | pin);;
					return;
				}
			}
        } else {
            // Ignore
            return;
        }
        boolean keyUp = !keyDown;

        // If keyUp then we send what we currently have.
        if (keyUp && lastKeyDown && brailleMap.containsKey(currentPinCode)) {
			sendKeyEvents(e.getComponent(), e.getWhen(), currentPinCode);
			if (shift == 1) shift = 0;
			if (digit == 1) digit = 0;
		}

		if (currentPinCode == ENTER) {
			if (shift < 3) shift = 0;
			if (digit < 3) digit = 0;
		}

		if (pin != null) {
			if (keyDown) {
				currentPinCode = currentPinCode | pin;
			} else {
				currentPinCode = ~(~currentPinCode | pin);
			}
			//log.info("CURRENT PINS: " + currentPinCode);
		}

        lastKeyDown = keyDown;

        //super.processKeyEvent(e);
    }


	private void sendKeyEvents(Component c, long when, int pins) {
		KeyData kd = brailleMap.get(pins);
		boolean whitespace = pins == ENTER || pins == SPACE;

		// Ignore character if it isn't in A-J, unless whitespace
		if (digit > 0 && !whitespace) {
			if (!numberMap.containsKey(pins)) return;
			kd = numberMap.get(pins);
		}
		char keyChar = kd.keyChar; // So that we don't alter the KeyData value in the map.

		// Shift blocks ENTER
		int modifiers = 0;
		if (shift > 0 && pins != ENTER) {
			modifiers = KeyEvent.SHIFT_DOWN_MASK;
			keyChar = Character.toUpperCase(keyChar);
		}

		log.info("SEND KEYEVENTS: " + pins +", " + kd.keyChar + ", " + kd.keyCode);
		KeyEvent newE = new KeyEvent(c,
									 KeyEvent.KEY_PRESSED,
									 when,
									 modifiers,
									 kd.keyCode,
									 keyChar);
		super.processKeyEvent(newE);
		newE = new KeyEvent(c,
							KeyEvent.KEY_TYPED,
							when,
							modifiers,
							KeyEvent.VK_UNDEFINED,
							keyChar);
		super.processKeyEvent(newE);
		newE = new KeyEvent(c,
							KeyEvent.KEY_RELEASED,
							when,
							modifiers,
							kd.keyCode,
							keyChar);
		super.processKeyEvent(newE);
	}


	private void addToBrailleMap(int pinCode, KeyData keyData) {
		if (brailleMap.containsKey(pinCode)) {
			log.severe("DUPLICATE KEYCODE MAP: "  + pinCode);
			log.severe("    CURRENT: " + brailleMap.get(pinCode).keyChar);
			log.severe("    DESIRED: " + keyData.keyChar);
		} else { 
			brailleMap.put(pinCode, keyData);
		}
	}


    private void  populateBrailleMaps() {
		// LETTERS, PUNCTuATION
        addToBrailleMap(A, new KeyData('a'));
        addToBrailleMap(B, new KeyData('b'));
        addToBrailleMap(C, new KeyData('c'));
        addToBrailleMap(D, new KeyData('d'));
        addToBrailleMap(E, new KeyData('e'));
        addToBrailleMap(F, new KeyData('f'));
        addToBrailleMap(G, new KeyData('g'));
        addToBrailleMap(H, new KeyData('h'));
        addToBrailleMap(I, new KeyData('i'));
        addToBrailleMap(J, new KeyData('j'));
        addToBrailleMap(K, new KeyData('k'));
        addToBrailleMap(L, new KeyData('l'));
        addToBrailleMap(M, new KeyData('m'));
        addToBrailleMap(N, new KeyData('n'));
        addToBrailleMap(O, new KeyData('o'));
        addToBrailleMap(P, new KeyData('p'));
        addToBrailleMap(Q, new KeyData('q'));
        addToBrailleMap(R, new KeyData('r'));
        addToBrailleMap(S, new KeyData('s'));
        addToBrailleMap(T, new KeyData('t'));
        addToBrailleMap(U, new KeyData('u'));
        addToBrailleMap(V, new KeyData('v'));
        addToBrailleMap(W, new KeyData('w'));
        addToBrailleMap(X, new KeyData('x'));
        addToBrailleMap(Y, new KeyData('y'));
        addToBrailleMap(Z, new KeyData('z'));
        addToBrailleMap(ENTER, new KeyData('\n', KeyEvent.VK_ENTER));
	
		// NUMBERS
		// WITH NUMBER PREFiX
        numberMap.put(D0, new KeyData('0'));
        numberMap.put(D1, new KeyData('1'));
        numberMap.put(D2, new KeyData('2'));
        numberMap.put(D3, new KeyData('3'));
        numberMap.put(D4, new KeyData('4'));
        numberMap.put(D5, new KeyData('5'));
        numberMap.put(D6, new KeyData('6'));
        numberMap.put(D7, new KeyData('7'));
        numberMap.put(D8, new KeyData('8'));
        numberMap.put(D9, new KeyData('9'));
		// Duplicate in numberMap as we don't want to disable these if we use the digit prefix.
        numberMap.put(N0, numberMap.get(D0));
        numberMap.put(N1, numberMap.get(D1));
        numberMap.put(N2, numberMap.get(D2));
        numberMap.put(N3, numberMap.get(D3));
        numberMap.put(N4, numberMap.get(D4));
        numberMap.put(N5, numberMap.get(D5));
        numberMap.put(N6, numberMap.get(D6));
        numberMap.put(N7, numberMap.get(D7));
        numberMap.put(N8, numberMap.get(D8));
        numberMap.put(N9, numberMap.get(D9));
		// COMPUTER NOTATION
        addToBrailleMap(N0, numberMap.get(D0));
        addToBrailleMap(N1, numberMap.get(D1));
        addToBrailleMap(N2, numberMap.get(D2));
        addToBrailleMap(N3, numberMap.get(D3));
        addToBrailleMap(N4, numberMap.get(D4));
        addToBrailleMap(N5, numberMap.get(D5));
        addToBrailleMap(N6, numberMap.get(D6));
        addToBrailleMap(N7, numberMap.get(D7));
        addToBrailleMap(N8, numberMap.get(D8));
        addToBrailleMap(N9, numberMap.get(D9));

		// SIMPLE PUNCTUATION
		addToBrailleMap(APOSTROPHE, new KeyData('\''));
		addToBrailleMap(COLON, new KeyData(':', KeyEvent.VK_COLON));
		addToBrailleMap(COMMA, new KeyData(',', KeyEvent.VK_COMMA));
		addToBrailleMap(EXCLAMATION, new KeyData('!', KeyEvent.VK_EXCLAMATION_MARK));
		addToBrailleMap(FULLSTOP, new KeyData('.', KeyEvent.VK_PERIOD));
		addToBrailleMap(MINUS, new KeyData('-', KeyEvent.VK_MINUS));
		addToBrailleMap(QUESTION, new KeyData('?'));
		addToBrailleMap(QUOTE, new KeyData('"'));
		addToBrailleMap(SEMICOLON, new KeyData(';', KeyEvent.VK_SEMICOLON));


		// ASCII CHARACTERS. Stored under the negative of their keycode.
        addToBrailleMap(-KeyEvent.VK_ENTER, brailleMap.get(ENTER));
        addToBrailleMap(-KeyEvent.VK_SPACE, new KeyData(' ', KeyEvent.VK_SPACE));

        keyPinMap.put(70, 1);   // F
        keyPinMap.put(68, 2);   // D
        keyPinMap.put(83, 4);   // S
        keyPinMap.put(74, 8);   // J
        keyPinMap.put(75, 16);  // K
        keyPinMap.put(76, 32);  // L
        keyPinMap.put(65, 64);  // A
        keyPinMap.put(59, 128); // ;
    }

	// PINCODES
	// LETTERS
	public static final int A = 1;
	public static final int B = 3;
	public static final int C = 9;
	public static final int D = 25;
	public static final int E = 17;
	public static final int F = 11;
	public static final int G = 27;
	public static final int H = 19;
	public static final int I = 10;
	public static final int J = 26;
	public static final int K = 5;
	public static final int L = 7;
	public static final int M = 13;
	public static final int N = 29;
	public static final int O = 21;
	public static final int P = 15;
	public static final int Q = 31;
	public static final int R = 23;
	public static final int S = 14;
	public static final int T = 30;
	public static final int U = 37;
	public static final int V = 39;
	public static final int W = 58;
	public static final int X = 45;
	public static final int Y = 61;
	public static final int Z = 53;

	// NUMBERS
	public static final int N0 = 63;
	public static final int N1 = 33;
	public static final int N2 = 35;
	public static final int N3 = 41;
	public static final int N4 = 57;
	public static final int N5 = 49;
	public static final int N6 = 43;
	public static final int N7 = 59;
	public static final int N8 = 51;
	public static final int N9 = 42;
	public static final int D1 = A;
	public static final int D2 = B;
	public static final int D3 = C;
	public static final int D4 = D;
	public static final int D5 = E;
	public static final int D6 = F;
	public static final int D7 = G;
	public static final int D8 = H;
	public static final int D9 = I;
	public static final int D0 = J;

	// PUNCTUATION
	public static final int APOSTROPHE = 4;
	public static final int COLON = 18;
	public static final int COMMA = 2;
	public static final int EXCLAMATION = 22;
	public static final int FULLSTOP = 50;
	public static final int HYPHEN = 36;
	public static final int MINUS = 36;
	public static final int QUESTION = 38;
	public static final int QUOTE = 8;
	public static final int SEMICOLON = 6;

	// SPECIAL
	public static final int DIGIT = 60;
	public static final int ENTER = 128;
	public static final int SHIFT = 32;
	public static final int SPACE = -32;
}