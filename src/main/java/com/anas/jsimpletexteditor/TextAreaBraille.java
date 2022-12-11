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
    private HashMap<Integer, KeyData> brailleMap = new HashMap<Integer, KeyData>();
    private HashMap<Integer, KeyData> numberMap = new HashMap<Integer, KeyData>();
    private HashMap<Integer, Integer> keyPinMap = new HashMap<Integer, Integer>();
    Logger log = Logger.getLogger("TextAreaBraille");

    private int currentPins = 0;
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
				if (currentPins == SHIFT) {
					shift++;
					if (shift > 3) shift = 0;
					if (digit == 1) digit = 0;
					currentPins = 0;
					return;
				}
				// Number
				if (currentPins == DIGIT) {
					digit++;
					if (digit > 3) digit = 0;
					if (shift == 1) shift = 0;
					currentPins = ~(~currentPins | pin);;
					return;
				}
			}
        } else {
            // Ignore
            return;
        }
        boolean keyUp = !keyDown;

        // If keyUp then we send what we currently have.
        if (keyUp && lastKeyDown && brailleMap.containsKey(currentPins)) {
			sendKeyEvents(e.getComponent(), e.getWhen(), currentPins);
			if (shift == 1) shift = 0;
			if (digit == 1) digit = 0;
		}

		if (currentPins == ENTER) {
			if (shift < 3) shift = 0;
			if (digit < 3) digit = 0;
		}

		if (pin != null) {
			if (keyDown) {
				currentPins = currentPins | pin;
			} else {
				currentPins = ~(~currentPins | pin);
			}
			//log.info("CURRENT PINS: " + currentPins);
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


    private void  populateBrailleMaps() {
		// LETTERS, PUNCTuATION
        brailleMap.put(A, new KeyData('a'));
        brailleMap.put(B, new KeyData('b'));
        brailleMap.put(C, new KeyData('c'));
        brailleMap.put(D, new KeyData('d'));
        brailleMap.put(E, new KeyData('e'));
        brailleMap.put(F, new KeyData('f'));
        brailleMap.put(G, new KeyData('g'));
        brailleMap.put(H, new KeyData('h'));
        brailleMap.put(I, new KeyData('i'));
        brailleMap.put(J, new KeyData('j'));
        brailleMap.put(K, new KeyData('k'));
        brailleMap.put(L, new KeyData('l'));
        brailleMap.put(M, new KeyData('m'));
        brailleMap.put(N, new KeyData('n'));
        brailleMap.put(O, new KeyData('o'));
        brailleMap.put(P, new KeyData('p'));
        brailleMap.put(Q, new KeyData('q'));
        brailleMap.put(R, new KeyData('r'));
        brailleMap.put(S, new KeyData('s'));
        brailleMap.put(T, new KeyData('t'));
        brailleMap.put(U, new KeyData('u'));
        brailleMap.put(V, new KeyData('v'));
        brailleMap.put(W, new KeyData('w'));
        brailleMap.put(X, new KeyData('x'));
        brailleMap.put(Y, new KeyData('y'));
        brailleMap.put(Z, new KeyData('z'));
        brailleMap.put(ENTER, new KeyData('\n', KeyEvent.VK_ENTER));

		// NUMBERS
        numberMap.put(D1, new KeyData('1'));
        numberMap.put(D2, new KeyData('2'));
        numberMap.put(D3, new KeyData('3'));
        numberMap.put(D4, new KeyData('4'));
        numberMap.put(D5, new KeyData('5'));
        numberMap.put(D6, new KeyData('6'));
        numberMap.put(D7, new KeyData('7'));
        numberMap.put(D8, new KeyData('8'));
        numberMap.put(D9, new KeyData('9'));
        numberMap.put(D0, new KeyData('0'));

		// ASCII CHARACTERS. Stored under the negative of their keycode.
        brailleMap.put(-KeyEvent.VK_ENTER, brailleMap.get(ENTER));
        brailleMap.put(-KeyEvent.VK_SPACE, new KeyData(' ', KeyEvent.VK_SPACE));

        keyPinMap.put(70, 1);   // F
        keyPinMap.put(68, 2);   // D
        keyPinMap.put(83, 4);   // S
        keyPinMap.put(74, 8);   // J
        keyPinMap.put(75, 16);  // K
        keyPinMap.put(76, 32);  // L
        keyPinMap.put(65, 64);  // A
        keyPinMap.put(59, 128); // ;
    }

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
	public static final int DIGIT = 60;
	public static final int ENTER = 128;
	public static final int SHIFT = 32;
	public static final int SPACE = -32;
}