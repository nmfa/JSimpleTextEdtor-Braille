package com.anas.jsimpletexteditor;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;
import javax.swing.JTextArea;


class MapData {
	public ArrayList<KeyData> keyData;
	public HashMap<Integer, MapData> map;
    Logger log = Logger.getLogger("MapData");

	MapData() {
		keyData = null;
		map = new HashMap<Integer, MapData>();
	}

	MapData(KeyData kd) {
		keyData = new ArrayList<KeyData>(1);
		keyData.add(kd);
		map = null; // to be explicit
	}

	MapData(ArrayList<KeyData> kd) {
		keyData = kd;
		map = null;
	}
}

class KeyData {
    public char keyChar;
    public int keyCode;
	public int modifiers;

    KeyData(char ch) {
        keyChar = ch;
        keyCode = (int)Character.toUpperCase(keyChar);
		modifiers = 0;
    }

    KeyData(char ch, boolean sh) {
        keyChar = ch;
        keyCode = (int)Character.toUpperCase(keyChar);
		modifiers = KeyEvent.SHIFT_DOWN_MASK;
    }

    KeyData(char ch, int co) {
        keyChar = ch;
        keyCode = co;
		modifiers = 0;
    }

	KeyData(char ch, int co, boolean sh) {
		keyChar = ch;
		keyCode = co;
		modifiers = KeyEvent.SHIFT_DOWN_MASK;
	}
}




public class TextAreaBraille extends JTextArea {
	// pinCode, MapData
    private HashMap<Integer, MapData> brailleMap = new HashMap<Integer, MapData>();
	// keyCode, pinCode bit
    private HashMap<Integer, Integer> keyPinMap = new HashMap<Integer, Integer>();
    Logger log = Logger.getLogger("TextAreaBraille");

    private int currentPinCode = 0;
	private ArrayList<Integer> currentPinCodesList = new ArrayList<Integer>();
    private boolean lastKeyDown = false;
	private int shift = 0; // 0 = no shift, 1 = normal, 2 = word, 3 = Caps Lock
	private int digit = 0; // 0 = not a digit, 1 = normal, 2 = word, 3 = Num Lock

    TextAreaBraille() {
        super();
        populateBrailleMaps();
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
		log.info("PROCESS KEY EVENT: " + currentPinCode + ", " + currentPinCodesList.toString() + ", " + keyPinMap.getOrDefault(e.getKeyCode(), 0));
		// Not setting lastKeyDown here, as it'll make the onKeyDown and onKeyUp logic consistent for when it is
		// moved to an API which has those functions, rather than this single API function.
		if (e.getID() == KeyEvent.KEY_PRESSED) {
			onKeyDown(e);
        } else if (e.getID() == KeyEvent.KEY_RELEASED) {
			onKeyUp(e);
        } else {
            // Ignore
        }
		log.info("PROCESSED: " + currentPinCode + ", " + currentPinCodesList.toString());
    }


	private void onKeyDown(KeyEvent e) {
		// Ignore for keyDown
		int keyCode = e.getKeyCode();
		switch (keyCode) {
			case KeyEvent.VK_ENTER:
			case KeyEvent.VK_SPACE:
			case KeyEvent.VK_BACK_SPACE:
				return;
		}
		Integer pin = keyPinMap.getOrDefault(keyCode, 0);
		currentPinCode = currentPinCode | pin;
		lastKeyDown = true;
	}


	private void onKeyUp(KeyEvent e) {
		int keyCode = e.getKeyCode();
		Integer pin = keyPinMap.getOrDefault(keyCode, 0);

		// Pass through. Reset shift below Caps Lock (3) for white space.
		switch (keyCode) {
			case KeyEvent.VK_ENTER:
			case KeyEvent.VK_SPACE:
			case KeyEvent.VK_BACK_SPACE:
				sendKeyEvents(e.getComponent(), e. getWhen(), brailleMap.get(-keyCode).keyData);
				if (shift < 3) shift = 0;
				if (digit < 3) digit = 0;
				currentPinCodesList.clear();
				if (shift > 0) currentPinCodesList.add(SHIFT);
				if (digit > 0) currentPinCodesList.add(DIGIT);
				lastKeyDown = false;
				return;
		}

		// Getting engaged when it's the last key left of a larger combination, so only after a keyDown
		// Can't have shift and digit engaged simultaneously, as they both use A-J.
		if (lastKeyDown && (currentPinCode == SHIFT || currentPinCode == DIGIT)) {
			// Shift
			if (currentPinCode == SHIFT) {
				shift++;
				if (shift > 3) shift = 0;
				if (digit > 0) digit = 0;
				currentPinCode = 0;
				log.info("SHIFT: " + shift);
			}
			// Number
			if (currentPinCode == DIGIT) {
				digit++;
				if (digit > 3) digit = 0;
				if (shift > 0) shift = 0;
				currentPinCode = ~(~currentPinCode | pin);
				log.info("DIGIT: " + digit);
			}
			// Ensure the pin code sequence is correct.
			// Shift or digit start a pin code sequence, so just reset,
			currentPinCodesList.clear();
			if (shift > 0) currentPinCodesList.add(SHIFT);
			if (digit > 0) currentPinCodesList.add(DIGIT);
			lastKeyDown = false;
			return;
		}
		log.info("HERE: " + currentPinCode + ", " + currentPinCodesList.toString() + ", " + shift);

        // If keyUp then find the assocaited MapData, if it exists.
        if (lastKeyDown) {
			currentPinCodesList.add(currentPinCode);
			log.info("PIN CODE SEQUENCE: " + currentPinCodesList.toString());
			MapData md = getMapDataFromPinCodes();
			if (md == null) {
				// Invalid pin code sequence. Reset.
			} else if (md.keyData != null) {
				// Send character sequence.
				sendKeyEvents(e.getComponent(), e.getWhen(), md.keyData);
				if (shift == 1) shift = 0;
				if (digit == 1) digit = 0;
			} else if (md.map != null) {
				// Get next pin code.
				currentPinCode = ~(~currentPinCode | pin);
				lastKeyDown = false;
				return;
			} else {
				// Something has gone wrong.
				log.severe("FOuND EMPTY MAPDATA IN BRAILLE MAP.");
			}
			// Reset the pin code sequence, as one way or another it is done with.
			currentPinCodesList.clear();

			// Deal with word locks of shift and digit
			if (currentPinCode == ENTER || currentPinCode == SPACE) {
				if (shift < 3) shift = 0;
				if (digit < 3) digit = 0;
			}
			if (shift == 1) shift = 0;
			if (digit == 1) digit = 0; 
			// If shift or digit are locked, add to the pin code sequence now.
			// They won't both be set.
			if (shift > 0) currentPinCodesList.add(SHIFT);
			if (digit > 0) currentPinCodesList.add(DIGIT);
		}

		currentPinCode = ~(~currentPinCode | pin);
		lastKeyDown = false;
	}


	private MapData getMapDataFromPinCodes() {
		HashMap<Integer, MapData> map = brailleMap;
		MapData md = null;
		for (int code: currentPinCodesList) {
			if (map == null) break;
			md = map.get(code);
			if (md == null) break;
			map = md.map;
		}
		return md;
	}


	private void sendKeyEvents(Component c, long when, ArrayList<KeyData> keyData) {
		for (KeyData kd: keyData) {
			sendKeyEvents(c, when, kd);
		}
	}

	private void sendKeyEvents(Component c, long when, KeyData keyData) {
		log.info("SEND KEYEVENT: " + keyData.keyChar + ", " + keyData.keyCode);
		KeyEvent newE = new KeyEvent(c,
									 KeyEvent.KEY_PRESSED,
									 when,
									 keyData.modifiers,
									 keyData.keyCode,
									 keyData.keyChar);
		super.processKeyEvent(newE);
		newE = new KeyEvent(c,
							KeyEvent.KEY_TYPED,
							when,
							keyData.modifiers,
							KeyEvent.VK_UNDEFINED,
							keyData.keyChar);
		super.processKeyEvent(newE);
		newE = new KeyEvent(c,
							KeyEvent.KEY_RELEASED,
							when,
							keyData.modifiers,
							keyData.keyCode,
							keyData.keyChar);
		super.processKeyEvent(newE);
	}

	private void addToBrailleMap(int pinCode, KeyData keyData) {
		int[] pinCodes = {pinCode};
		ArrayList<KeyData> keyDataAL = new ArrayList<KeyData>();
		keyDataAL.add(keyData);
		addToBrailleMap(pinCodes, keyDataAL);
	}

	private void addToBrailleMap(int pinCode, ArrayList<KeyData> keyData) {
		int[] pinCodes = {pinCode};
		addToBrailleMap(pinCodes, keyData);
	}

	private void addToBrailleMap(int[] pinCodes, KeyData keyData) {
		ArrayList<KeyData> keyDataAL = new ArrayList<KeyData>();
		keyDataAL.add(keyData);
		addToBrailleMap(pinCodes, keyDataAL);
	}

	private void addToBrailleMap(int[] pinCodes, ArrayList<KeyData> keyData) {
		addToBrailleMap(pinCodes, 0, keyData, brailleMap);
	}

	private void addToBrailleMap(int[] pinCodes, int pcIndex, ArrayList<KeyData> keyData, HashMap<Integer, MapData> map) {
		if (map.containsKey(pinCodes[pcIndex])) {
			if (pcIndex + 1 == pinCodes.length) {
				log.severe("DUPLICATE KEYCODE MAP: "  + pinCodes);
				log.severe("    CURRENT: " + map.get(pinCodes[pcIndex]).keyData);
				log.severe("    DESIRED: " + keyData);
				return;
			} else {
				addToBrailleMap(pinCodes, pcIndex + 1, keyData, map.get(pinCodes[pcIndex]).map);
			}
		} else {
			if (pcIndex + 1 == pinCodes.length) {
				MapData md = new MapData(keyData);
				map.put(pinCodes[pcIndex], md);
			} else {
				MapData md = new MapData();
				map.put(pinCodes[pcIndex], md);
				addToBrailleMap(pinCodes, pcIndex + 1, keyData, md.map);
			}
		}
	}


	private static int[] join(int item1, int item2) {
		int[] newArr = new int[2];
		newArr[0] = item1;
		newArr[1] = item2;
		return newArr;
	}
/*
	private static int[] join(int[] arr, int item) {
		int[] newArr = Arrays.copyOf(arr, arr.length + 1);
		newArr[arr.length] = item;
		return newArr;
	}

	private static int[] join(int item, int[] arr) {
		int[] newArr = new int[arr.length + 1];
		newArr[0] = item;
		System.arraycopy(arr, 0, newArr, 1, arr.length);
		return newArr;
	}
*/

    private void  populateBrailleMaps() {
		// SOME COMMON KEYEVENTS
		final KeyData KD_BACK_SPACE = new KeyData('\b', KeyEvent.VK_BACK_SPACE);
		final KeyData KD_ENTER = new KeyData('\n', KeyEvent.VK_ENTER);
		final KeyData KD_SPACE = new KeyData(' ', KeyEvent.VK_SPACE);

		// LETTERS, PUNCTuATION
        addToBrailleMap(Aa, new KeyData('a'));
        addToBrailleMap(Ab, new KeyData('b'));
        addToBrailleMap(Ac, new KeyData('c'));
        addToBrailleMap(Ad, new KeyData('d'));
        addToBrailleMap(Ae, new KeyData('e'));
        addToBrailleMap(Af, new KeyData('f'));
        addToBrailleMap(Ag, new KeyData('g'));
        addToBrailleMap(Ah, new KeyData('h'));
        addToBrailleMap(Ai, new KeyData('i'));
        addToBrailleMap(Aj, new KeyData('j'));
        addToBrailleMap(Ak, new KeyData('k'));
        addToBrailleMap(Al, new KeyData('l'));
        addToBrailleMap(Am, new KeyData('m'));
        addToBrailleMap(An, new KeyData('n'));
        addToBrailleMap(Ao, new KeyData('o'));
        addToBrailleMap(Ap, new KeyData('p'));
        addToBrailleMap(Aq, new KeyData('q'));
        addToBrailleMap(Ar, new KeyData('r'));
        addToBrailleMap(As, new KeyData('s'));
        addToBrailleMap(At, new KeyData('t'));
        addToBrailleMap(Au, new KeyData('u'));
        addToBrailleMap(Av, new KeyData('v'));
        addToBrailleMap(Aw, new KeyData('w'));
        addToBrailleMap(Ax, new KeyData('x'));
        addToBrailleMap(Ay, new KeyData('y'));
        addToBrailleMap(Az, new KeyData('z'));
        addToBrailleMap(AA, new KeyData('A', true));
        addToBrailleMap(AB, new KeyData('B', true));
        addToBrailleMap(AC, new KeyData('C', true));
        addToBrailleMap(AD, new KeyData('D', true));
        addToBrailleMap(AE, new KeyData('E', true));
        addToBrailleMap(AF, new KeyData('F', true));
        addToBrailleMap(AG, new KeyData('G', true));
        addToBrailleMap(AH, new KeyData('H', true));
        addToBrailleMap(AI, new KeyData('I', true));
        addToBrailleMap(AJ, new KeyData('J', true));
        addToBrailleMap(AK, new KeyData('K', true));
        addToBrailleMap(AL, new KeyData('L', true));
        addToBrailleMap(AM, new KeyData('M', true));
        addToBrailleMap(AN, new KeyData('N', true));
        addToBrailleMap(AO, new KeyData('O', true));
        addToBrailleMap(AP, new KeyData('P', true));
        addToBrailleMap(AQ, new KeyData('Q', true));
        addToBrailleMap(AR, new KeyData('R', true));
        addToBrailleMap(AS, new KeyData('S', true));
        addToBrailleMap(AT, new KeyData('T', true));
        addToBrailleMap(AU, new KeyData('U', true));
        addToBrailleMap(AV, new KeyData('V', true));
        addToBrailleMap(AW, new KeyData('W', true));
        addToBrailleMap(AX, new KeyData('X', true));
        addToBrailleMap(AY, new KeyData('Y', true));
        addToBrailleMap(AZ, new KeyData('Z', true));
        addToBrailleMap(ENTER, KD_ENTER);
        addToBrailleMap(join(DIGIT, ENTER), KD_ENTER);
        addToBrailleMap(join(SHIFT, ENTER), KD_ENTER);
	
		// NUMBERS
		// COMPUTER NOTATION
        addToBrailleMap(N0, new KeyData('0'));
        addToBrailleMap(N1, new KeyData('1'));
        addToBrailleMap(N2, new KeyData('2'));
        addToBrailleMap(N3, new KeyData('3'));
        addToBrailleMap(N4, new KeyData('4'));
        addToBrailleMap(N5, new KeyData('5'));
        addToBrailleMap(N6, new KeyData('6'));
        addToBrailleMap(N7, new KeyData('7'));
        addToBrailleMap(N8, new KeyData('8'));
        addToBrailleMap(N9, new KeyData('9'));
		// WITH DIGIT PREFIX
        addToBrailleMap(D0, brailleMap.get(N0).keyData);
        addToBrailleMap(D1, brailleMap.get(N1).keyData);
        addToBrailleMap(D2, brailleMap.get(N2).keyData);
        addToBrailleMap(D3, brailleMap.get(N3).keyData);
        addToBrailleMap(D4, brailleMap.get(N4).keyData);
        addToBrailleMap(D5, brailleMap.get(N5).keyData);
        addToBrailleMap(D6, brailleMap.get(N6).keyData);
        addToBrailleMap(D7, brailleMap.get(N7).keyData);
        addToBrailleMap(D8, brailleMap.get(N8).keyData);
        addToBrailleMap(D9, brailleMap.get(N9).keyData);

		// SIMPLE PUNCTUATION
		addToBrailleMap(APOSTROPHE, new KeyData('\''));
		addToBrailleMap(COLON, new KeyData(':', KeyEvent.VK_COLON, true));
		addToBrailleMap(COMMA, new KeyData(',', KeyEvent.VK_COMMA));
		addToBrailleMap(EXCLAMATION, new KeyData('!', KeyEvent.VK_EXCLAMATION_MARK, true));
		addToBrailleMap(FULLSTOP, new KeyData('.', KeyEvent.VK_PERIOD));
		addToBrailleMap(MINUS, new KeyData('-', KeyEvent.VK_MINUS));
		addToBrailleMap(PRIME, new KeyData('′'));
		addToBrailleMap(QUESTION, new KeyData('?', true));
		//addToBrailleMap(QUOTE, new KeyData('"', true));
		addToBrailleMap(SEMICOLON, new KeyData(';', KeyEvent.VK_SEMICOLON));

		// SHIFT PUNCTUATION
		addToBrailleMap(ANGLE_BRACKET_OPEN, new KeyData('<', true));
		addToBrailleMap(ANGLE_BRACKET_CLOSE, new KeyData('>', true));
		addToBrailleMap(BACK_SLASH, new KeyData('\\', KeyEvent.VK_BACK_SLASH));
		addToBrailleMap(CURLY_BRACKET_OPEN, new KeyData('{', true));
		addToBrailleMap(CURLY_BRACKET_CLOSE, new KeyData('}', true));
		addToBrailleMap(FORWARD_SLASH, new KeyData('/', KeyEvent.VK_SLASH));
		addToBrailleMap(ROUND_BRACKET_OPEN, new KeyData('(', true));
		addToBrailleMap(ROUND_BRACKET_CLOSE, new KeyData(')', true));
		addToBrailleMap(SQUARE_BRACKET_OPEN, new KeyData('['));
		addToBrailleMap(SQUARE_BRACKET_CLOSE, new KeyData(']'));
		addToBrailleMap(UNDERSCORE, new KeyData('_', KeyEvent.VK_UNDERSCORE, true));

		// ASCII CHARACTERS. Stored under the negative of their keycode.
        addToBrailleMap(-KeyEvent.VK_ENTER, brailleMap.get(ENTER).keyData);
        addToBrailleMap(-KeyEvent.VK_SPACE, KD_SPACE);
    	addToBrailleMap(-KeyEvent.VK_BACK_SPACE, KD_BACK_SPACE);

		// MAP REAL KEYBOARD TO PINS
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
	// SPECIAL
	public static final int DIGIT = 60;
	public static final int ENTER = 128;
	public static final int SHIFT8 = 8;
	public static final int SHIFT16 = 16;
	public static final int SHIFT24 = 24;
	public static final int SHIFT32 = 32;
	public static final int SHIFT40 = 40;
	public static final int SHIFT48 = 48;
	public static final int SHIFT56 = 56;
	public static final int SHIFT = SHIFT32;
	public static final int SPACE = -KeyEvent.VK_SPACE;

	// LETTERS
	public static final int Aa = 1;
	public static final int Ab = 3;
	public static final int Ac = 9;
	public static final int Ad = 25;
	public static final int Ae = 17;
	public static final int Af = 11;
	public static final int Ag = 27;
	public static final int Ah = 19;
	public static final int Ai = 10;
	public static final int Aj = 26;
	public static final int Ak = 5;
	public static final int Al = 7;
	public static final int Am = 13;
	public static final int An = 29;
	public static final int Ao = 21;
	public static final int Ap = 15;
	public static final int Aq = 31;
	public static final int Ar = 23;
	public static final int As = 14;
	public static final int At = 30;
	public static final int Au = 37;
	public static final int Av = 39;
	public static final int Aw = 58;
	public static final int Ax = 45;
	public static final int Ay = 61;
	public static final int Az = 53;
	public static final int[] AA = join(SHIFT, 1);
	public static final int[] AB = join(SHIFT, 3);
	public static final int[] AC = join(SHIFT, 9);
	public static final int[] AD = join(SHIFT, 25);
	public static final int[] AE = join(SHIFT, 17);
	public static final int[] AF = join(SHIFT, 11);
	public static final int[] AG = join(SHIFT, 27);
	public static final int[] AH = join(SHIFT, 19);
	public static final int[] AI = join(SHIFT, 10);
	public static final int[] AJ = join(SHIFT, 26);
	public static final int[] AK = join(SHIFT, 5);
	public static final int[] AL = join(SHIFT, 7);
	public static final int[] AM = join(SHIFT, 13);
	public static final int[] AN = join(SHIFT, 29);
	public static final int[] AO = join(SHIFT, 21);
	public static final int[] AP = join(SHIFT, 15);
	public static final int[] AQ = join(SHIFT, 31);
	public static final int[] AR = join(SHIFT, 23);
	public static final int[] AS = join(SHIFT, 14);
	public static final int[] AT = join(SHIFT, 30);
	public static final int[] AU = join(SHIFT, 37);
	public static final int[] AV = join(SHIFT, 39);
	public static final int[] AW = join(SHIFT, 58);
	public static final int[] AX = join(SHIFT, 45);
	public static final int[] AY = join(SHIFT, 61);
	public static final int[] AZ = join(SHIFT, 53);

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
	public static final int[] D1 = join(DIGIT, Aa);
	public static final int[] D2 = join(DIGIT, Ab);
	public static final int[] D3 = join(DIGIT, Ac);
	public static final int[] D4 = join(DIGIT, Ad);
	public static final int[] D5 = join(DIGIT, Ae);
	public static final int[] D6 = join(DIGIT, Af);
	public static final int[] D7 = join(DIGIT, Ag);
	public static final int[] D8 = join(DIGIT, Ah);
	public static final int[] D9 = join(DIGIT, Ai);
	public static final int[] D0 = join(DIGIT, Aj);

	// SIMPLE PUNCTUATION
	public static final int APOSTROPHE = 4;
	public static final int COLON = 18;
	public static final int COMMA = 2;
	public static final int EXCLAMATION = 22;
	public static final int FULLSTOP = 50;
	public static final int HYPHEN = 36;
	public static final int MINUS = 36;
	public static final int PRIME = 54;
	public static final int QUESTION = 38;
	public static final int SEMICOLON = 6;

	// SHIFT PUNCTUATION
	public static final int GROUP_OPEM = 35; // same as N2
	public static final int GROUP_CLOSE = 28;
	public static final int[] ANGLE_BRACKET_OPEN = join(SHIFT8, GROUP_OPEM);
	public static final int[] ANGLE_BRACKET_CLOSE = join(SHIFT8, GROUP_CLOSE);
	public static final int[] BACK_SLASH = join(SHIFT56, 33);
	public static final int[] CURLY_BRACKET_OPEN = join(SHIFT56, GROUP_OPEM);
	public static final int[] CURLY_BRACKET_CLOSE = join(SHIFT56, GROUP_CLOSE);
	public static final int[] FORWARD_SLASH = join(SHIFT56, 12);
	public static final int[] ROUND_BRACKET_OPEN = join(SHIFT16, GROUP_OPEM);
	public static final int[] ROUND_BRACKET_CLOSE = join(SHIFT16, GROUP_CLOSE);
	public static final int[] SQUARE_BRACKET_OPEN = join(SHIFT40, GROUP_OPEM);
	public static final int[] SQUARE_BRACKET_CLOSE = join(SHIFT40, GROUP_CLOSE);
	public static final int[] UNDERSCORE = join(SHIFT40, HYPHEN);

}