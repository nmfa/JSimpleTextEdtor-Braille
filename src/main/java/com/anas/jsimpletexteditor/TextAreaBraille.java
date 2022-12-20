package com.anas.jsimpletexteditor;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Arrays;
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

	public void addMap() {
		if (map == null) map = new HashMap<Integer, MapData>();
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
	private int shift40 = 0;
	private int digit = 0; // 0 = not a digit, 1 = normal, 2 = word, 3 = Num Lock
	private int pinCodeOverflow = 0; // Depth of overflow when doing prime, double prime type examples.

    TextAreaBraille() {
        super();
        populateBrailleMaps();
		// Initialise as if we've just had whitespace, arbitrarily ENTER.
		currentPinCodesList.add(ENTER);
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
		// Almost gnore for keyDown
		int keyCode = e.getKeyCode();
		switch (keyCode) {
			case KeyEvent.VK_ENTER:
			case KeyEvent.VK_SPACE:
			case KeyEvent.VK_BACK_SPACE:
				lastKeyDown = true;
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
				currentPinCode = ENTER;
				break;

			case KeyEvent.VK_SPACE:
				currentPinCode = SPACE;
				break;

			case KeyEvent.VK_BACK_SPACE:
				sendKeyEvents(e.getComponent(), e. getWhen(), brailleMap.get(-keyCode).keyData);
				if (shift < 3) shift = 0;
				if (shift40 < 3) shift40 = 0;
				if (digit < 3) digit = 0;
				currentPinCodesList.clear();
				if (shift > 0) currentPinCodesList.add(SHIFT);
				//Shifts can combine, main shft first.
				if (shift40 > 0) currentPinCodesList.add(SHIFT40);
				// This should never combine with the shifts.
				if (digit > 0) currentPinCodesList.add(DIGIT);
				lastKeyDown = false;
				return;
		}

		// Getting engaged when it's the last key left of a larger combination, so only after a keyDown
		// Can't have shift and digit engaged simultaneously, as they both use A-J.
		if (lastKeyDown && 
			(currentPinCode == SHIFT ||
			 currentPinCode == SHIFT40 || 
			 currentPinCode == DIGIT)) {
			if (!(currentPinCodesList.size() > 0 && currentPinCodesList.get(0) == SHIFT8)) {
				log.info("NOT SHIFT 8");
				// Shift
				if (currentPinCode == SHIFT) {
					shift++;
					if (shift > 3) shift = 0;
					// Doesn't reset other shifts
					if (digit > 0) digit = 0;
					currentPinCode = 0;
					log.info("SHIFT: " + shift);
				}
				// Shift 40
				if (currentPinCode == SHIFT40) {
					shift40++;
					if (shift40 > 3) shift40 = 0;
					// Doesn't reset other shifts
					if (digit > 0) digit = 0;
					currentPinCode = ~(~currentPinCode | pin);
					log.info("SHIFT40: " + shift40);
				}
				// Number
				if (currentPinCode == DIGIT) {
					digit++;
					if (digit > 3) digit = 0;
					// Resets both shifts.
					if (shift > 0) shift = 0;
					if (shift40 > 0) shift40 = 0;
					currentPinCode = ~(~currentPinCode | pin);
					log.info("DIGIT: " + digit);
				}
				// Ensure the pin code sequence is correct.
				// Shift or digit start a pin code sequence, so just reset,
				popModifiers();
				if (shift > 0) currentPinCodesList.add(SHIFT);
				if (shift40 > 0) currentPinCodesList.add(SHIFT40);
				if (digit > 0) currentPinCodesList.add(DIGIT);
				lastKeyDown = false;
				return;
			}
		}
		log.info("HERE: " + currentPinCode + ", " + currentPinCodesList.toString() + ", " + shift);

        // If keyUp then find the assocaited MapData, if it exists.
        if (lastKeyDown) {
			if (isWhitespace(currentPinCode)) popModifiers();
			currentPinCodesList.add(currentPinCode);
			log.info("PIN CODE SEQUENCE: " + currentPinCodesList.toString() + ": " + pinCodeOverflow);
			MapData md = getMapDataFromPinCodes();
			if (md == null) {
				// Invalid pin code sequence. Need to account for pin code overflows.
				// This should only ever recurse once.
				if (pinCodeOverflow > 0) {
					currentPinCodesList.clear();
					pinCodeOverflow = 0;
					if (shift == 1) shift = 0;
					if (shift40 == 1) shift40 = 0;
					if (digit == 1) digit = 0; 
					if (shift > 0) currentPinCodesList.add(SHIFT);
					if (shift40 > 0) currentPinCodesList.add(SHIFT40);
					if (digit > 0) currentPinCodesList.add(DIGIT);
					onKeyUp(e);
					return;
				}
			} else if (md.keyData != null || md.map != null) {
				if (md.keyData != null) {
					// Send character sequence.
					sendKeyEvents(e.getComponent(), e.getWhen(), md.keyData);
					pinCodeOverflow = 0;
				}
				// We may possibly be doing a prime, double prime situation.
				if (md.map != null) {
					// Get next pin code.
					pinCodeOverflow = currentPinCodesList.size();
				}
			} else {
				// Something has gone wrong.
				log.severe("FOuND EMPTY MAPDATA IN BRAILLE MAP.");
			}

			if (pinCodeOverflow == 0) {
				// Reset the pin code sequence, as one way or another it is done with.
				currentPinCodesList.clear();
				pinCodeOverflow = 0;
			}
			// Unless WHITESPACE which is special case which terminate and start anoth overflow.
			// I don't think any other character has this property.
			// But this needs to happen regardless of the current overflow status.
			// There shouldn't be any situations where ENTER will be in an existing oveeflow
			// when ENTER is the current pin code.
			if (isWhitespace(currentPinCode) &&
				(currentPinCodesList.size() == 0 ||
				 (currentPinCodesList.size() > 0 && !isWhitespace(currentPinCodesList.get(0))))) {
				  currentPinCodesList.add(0, currentPinCode);
				pinCodeOverflow++;
			}

			// Deal with word locks of shift and digit
			if (isWhitespace(currentPinCode)) {
				if (shift < 3) shift = 0;
				if (shift40 < 3) shift40 = 0;
				if (digit < 3) digit = 0;
			}

			if (pinCodeOverflow == 0) {
				if (shift == 1) shift = 0;
				if (shift40 == 1) shift40 = 0;
				if (digit == 1) digit = 0; 
				// If shift or digit are locked, add to the pin code sequence now.
				// They won't both be set. Shifts can be set together.
				if (shift > 0) currentPinCodesList.add(SHIFT);
				if (shift40 > 0) currentPinCodesList.add(SHIFT40);
				if (digit > 0) currentPinCodesList.add(DIGIT);
			}
		}

		if (currentPinCode == SPACE) {
			currentPinCode = 0;
		} else {
			currentPinCode = ~(~currentPinCode | pin);
		}
		lastKeyDown = false;
	}


	// Called with whitespace to remove preceding shifts and digits.
	private void popModifiers() {
		if (currentPinCodesList.size() == 0) return;
		int finalIndex = currentPinCodesList.size() - 1;
		int finalPinCode = currentPinCodesList.get(finalIndex);
		if (((finalPinCode & SHIFT56) > 0 && (finalPinCode & ~SHIFT56) == 0) || finalPinCode == DIGIT) {
			currentPinCodesList.remove(finalIndex);
			// For shift combinations.
			if (finalPinCode != DIGIT) popModifiers();
		}
	}

	private boolean isWhitespace(int pinCode) {
		for (int code: WHITESPACES) {
			if (pinCode == code) return true;
		}
		return false;
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

	private void addToBrailleMap(int[] pinCodes, KeyData... keyData) {
		ArrayList<KeyData> keyDataAL = new ArrayList<KeyData>();
		for (KeyData kd: keyData) {
			keyDataAL.add(kd);
		}
		addToBrailleMap(pinCodes, keyDataAL);
	}

	private void addToBrailleMap(int[] pinCodes, KeyData keyData1, KeyData keyData2) {
		ArrayList<KeyData> keyDataAL = new ArrayList<KeyData>();
		keyDataAL.add(keyData1);
		keyDataAL.add(keyData2);
		addToBrailleMap(pinCodes, keyDataAL);
	}

	private void addAlphabetToBrailleMap() {
		for (int i = 0; i < Alower.length; i++) {
			addToBrailleMap(Alower[i], KD_Alower[i]);
			addToBrailleMap(join(SHIFT, Alower[i]), KD_AUPPER[i]);
		}
	}

	private void addCombiningCharsToBrailleMap() {
		for (int c = 0; c < COMBINING.length; c++) {
			for (int i = 0; i < Alower.length; i++) {
				String combination = String.valueOf(KD_Alower[i].keyChar) + String.valueOf(KD_COMBINING[c].keyChar);
				char keyChar = Normalizer.normalize(combination, Form.NFC).charAt(0);
				// Not every combination is canonical and has a Unicode character.
				if (keyChar != KD_Alower[i].keyChar) {
					addToBrailleMap(join(COMBINING[c], Alower[i]), new KeyData(keyChar));
				} else {
					addToBrailleMap(join(COMBINING[c], Alower[i]), join(KD_Alower[i], KD_COMBINING[c]));
				}
				combination = String.valueOf(KD_AUPPER[i].keyChar) + String.valueOf(KD_COMBINING[c].keyChar);
				keyChar = Normalizer.normalize(combination, Form.NFC).charAt(0);
				if (keyChar != KD_AUPPER[i].keyChar) {
					addToBrailleMap(join(SHIFT, COMBINING[c], Alower[i]), new KeyData(keyChar));
				} else {
					addToBrailleMap(join(SHIFT, COMBINING[c], Alower[i]), join(KD_AUPPER[i], KD_COMBINING[c]));
				}
			}
		}
	}

	private void addToBrailleMap(int[] pinCodes, ArrayList<KeyData> keyData) {
		addToBrailleMap(pinCodes, 0, keyData, brailleMap);
	}

	private void addToBrailleMap(int[] pinCodes, int pcIndex, ArrayList<KeyData> keyData, HashMap<Integer, MapData> map) {
		// If we have WHITESPACE , then we need to set up trees for each one.
		if (pinCodes[pcIndex] == WHITESPACE) {
			if (pcIndex == 0) {
				String sPinCodes = "";
				for (int pc: pinCodes) sPinCodes += pc + " ";
				log.info("WHITESPACE AT ZERO: " + sPinCodes);
			}
			// So far mappings only have one instance of a whitespace, so can keep simple.
			int whitespaceIndex = keyData.indexOf(KD_WHITESPACE);
			for (int i = 0; i < WHITESPACES.length; i++) {
				pinCodes[pcIndex] = WHITESPACES[i];
				if (whitespaceIndex >= 0) {
					keyData = new ArrayList<KeyData>(keyData);
					keyData.set(whitespaceIndex, KD_WHITESPACES[i]);
				}
				addToBrailleMap(pinCodes, pcIndex, keyData, map);
			}
			return;
		}

		MapData mapData =  map.get(pinCodes[pcIndex]);
		if (mapData != null) {
			if (pcIndex + 1 == pinCodes.length) {
				if (mapData.keyData != null) {
					String sPinCodes = "";
					for (int pc: pinCodes) sPinCodes += pc + " ";
					log.severe("DUPLICATE KEYCODE MAP: "  + sPinCodes);
					log.severe("    CURRENT: " + mapData.keyData.toString());
					log.severe("    DESIRED: " + keyData.toString());
				} else {
					mapData.keyData = keyData;
				}
			} else {
				// In the case where KeyData exists, but the same symbol can also be part of a combination
				// eg: rime & double prime
				if (mapData.map == null) mapData.addMap();
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


	private static int[] join(int... items) {
		return Arrays.copyOf(items, items.length);
	}

	private static KeyData[] join(KeyData... items) {
		return Arrays.copyOf(items, items.length);
	}

	private static int[] join(int[] arr, int... items) {
		int[] newArr = Arrays.copyOf(arr, arr.length + items.length);
		System.arraycopy(items, 0, newArr, arr.length, items.length);
		return newArr;
	}

	private static int[] join(int item, int[] arr) {
		int[] newArr = new int[arr.length + 1];
		newArr[0] = item;
		System.arraycopy(arr, 0, newArr, 1, arr.length);
		return newArr;
	}

	private static int[] join(int item1, int[] arr, int... items2) {
		return join(join(item1, arr), items2);
	}


    private void  populateBrailleMaps() {
		// ENGLISH ALPHABET
		addAlphabetToBrailleMap();
		addCombiningCharsToBrailleMap();

		addToBrailleMap(ENTER, KD_ENTER);
        addToBrailleMap(join(DIGIT, ENTER), KD_ENTER);
        addToBrailleMap(join(SHIFT, ENTER), KD_ENTER);
        addToBrailleMap(join(SHIFT40, ENTER), KD_ENTER);
        addToBrailleMap(join(SHIFT, SHIFT40, ENTER), KD_ENTER);
	
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
        addToBrailleMap(SHARP, new KeyData('♯'));

		// SIMPLE PUNCTUATION
		addToBrailleMap(APOSTROPHE, new KeyData('\''));
		addToBrailleMap(BACK_SLASH, new KeyData('\\', KeyEvent.VK_BACK_SLASH));
		addToBrailleMap(BULLET, new KeyData('•'));
		addToBrailleMap(CARET, new KeyData('^', true));
		addToBrailleMap(COLON, new KeyData(':', KeyEvent.VK_COLON, true));
		addToBrailleMap(COMMA, new KeyData(',', KeyEvent.VK_COMMA));
		addToBrailleMap(EXCLAMATION, new KeyData('!', KeyEvent.VK_EXCLAMATION_MARK, true));
		addToBrailleMap(FORWARD_SLASH, new KeyData('/', KeyEvent.VK_SLASH));
		addToBrailleMap(FULLSTOP, new KeyData('.', KeyEvent.VK_PERIOD));
		addToBrailleMap(HYPHEN, new KeyData('-', KeyEvent.VK_MINUS));
		addToBrailleMap(NUMBER, new KeyData('#'));
		addToBrailleMap(PRIME, new KeyData('′'));
		addToBrailleMap(QUESTION, new KeyData('?', true));
		//addToBrailleMap(QUOTE, new KeyData('"', true));
		addToBrailleMap(SEMICOLON, new KeyData(';', KeyEvent.VK_SEMICOLON));
		addToBrailleMap(TILDE, new KeyData('~', true));
		addToBrailleMap(UNDERSCORE, new KeyData('_', KeyEvent.VK_UNDERSCORE, true));

		// COMPLEX PUNCTUATION
		addToBrailleMap(DOUBLE_PRIME, KD_BACKSPACE, new KeyData('″'));

		// GROUP PUNCTUATION
		addToBrailleMap(ANGLE_BRACKET_OPEN, new KeyData('<', true));
		addToBrailleMap(ANGLE_BRACKET_CLOSE, new KeyData('>', true));
		addToBrailleMap(CURLY_BRACKET_OPEN, new KeyData('{', true));
		addToBrailleMap(CURLY_BRACKET_CLOSE, new KeyData('}', true));
		addToBrailleMap(ROUND_BRACKET_OPEN, new KeyData('(', true));
		addToBrailleMap(ROUND_BRACKET_CLOSE, new KeyData(')', true));
		addToBrailleMap(SQUARE_BRACKET_OPEN, new KeyData('['));
		addToBrailleMap(SQUARE_BRACKET_CLOSE, new KeyData(']'));

		// CURRENCY
		addToBrailleMap(CENT, new KeyData('¢'));
		addToBrailleMap(DOLLAR, new KeyData('$', KeyEvent.VK_DOLLAR, true));
		addToBrailleMap(EURO, new KeyData('€'));
		addToBrailleMap(FRANC, new KeyData('₣'));
		addToBrailleMap(GBP, new KeyData('£', true));
		addToBrailleMap(NAIRA, new KeyData('₦'));
		addToBrailleMap(YEN, new KeyData('¥'));

		// MATHS
		addToBrailleMap(ASTERISK, new KeyData('*', KeyEvent.VK_ASTERISK, true));
		addToBrailleMap(DITTO, new KeyData('"', true));
		addToBrailleMap(DIVIDE, new KeyData('÷'));
		addToBrailleMap(EQUALS, new KeyData('=', KeyEvent.VK_EQUALS));
		addToBrailleMap(MINUS, new KeyData('-', KeyEvent.VK_MINUS));
		addToBrailleMap(MULTIPLY, new KeyData('×'));
		addToBrailleMap(PERCENT, new KeyData('%', true));
		addToBrailleMap(PLUS, new KeyData('+', KeyEvent.VK_PLUS, true));
	
		// SYMBOLS
		addToBrailleMap(AMPERSAND, new KeyData('&', true));
		addToBrailleMap(AT_SIGN, new KeyData('@', true));
		addToBrailleMap(COPYRIGHT, new KeyData('©'));
		addToBrailleMap(DAGGER, new KeyData('†'));
		addToBrailleMap(DOUBLE_DAGGER, new KeyData('‡'));
		addToBrailleMap(DEGREES, new KeyData('°'));
		addToBrailleMap(FEMALE, new KeyData('♀'));
		addToBrailleMap(MALE, new KeyData('♂'));
		addToBrailleMap(PARAGRAPH, new KeyData('¶'));
		addToBrailleMap(REGISTERED, new KeyData('®'));
		addToBrailleMap(SECTION, new KeyData('§'));
		addToBrailleMap(TRADEMARK, new KeyData('™'));

		// ASCII CHARACTERS. Stored under the negative of their keycode.
        addToBrailleMap(-KeyEvent.VK_ENTER, brailleMap.get(ENTER).keyData);
        addToBrailleMap(-KeyEvent.VK_SPACE, KD_SPACE);
    	addToBrailleMap(-KeyEvent.VK_BACK_SPACE, KD_BACKSPACE);

		// GREEK ALPHABET
		addToBrailleMap(Galpha, new KeyData('α'));
		addToBrailleMap(Gbeta, new KeyData('β'));
		addToBrailleMap(Ggamma, new KeyData('γ'));
		addToBrailleMap(Gdelta, new KeyData('δ'));
		addToBrailleMap(Gepsilon, new KeyData('ε'));
		addToBrailleMap(Gzeta, new KeyData('ζ'));
		addToBrailleMap(Geta, new KeyData('η'));
		addToBrailleMap(Gtheta, new KeyData('θ'));
		addToBrailleMap(Giota, new KeyData('ι'));
		addToBrailleMap(Gkappa, new KeyData('κ'));
		addToBrailleMap(Glambda, new KeyData('λ'));
		addToBrailleMap(Gmu, new KeyData('μ'));
		addToBrailleMap(Gnu, new KeyData('ν'));
		addToBrailleMap(Gxi, new KeyData('ξ'));
		addToBrailleMap(Gomicron, new KeyData('ο'));
		addToBrailleMap(Gpi, new KeyData('π'));
		addToBrailleMap(Grho, new KeyData('ρ'));
		final KeyData KD_sigma = new KeyData('σ');
		addToBrailleMap(Gsigma, KD_sigma);
		addToBrailleMap(join(WHITESPACE, Gsigma), KD_sigma); // To prevent the nexy firing when just the letter σ.
		addToBrailleMap(join(Gsigma, WHITESPACE), KD_BACKSPACE, new KeyData('ς'), KD_WHITESPACE); // Sigma at the end of a word.
		addToBrailleMap(Gtau, new KeyData('τ'));
		addToBrailleMap(Gupsilon, new KeyData('υ'));
		addToBrailleMap(Gphi, new KeyData('φ'));
		addToBrailleMap(Gchi, new KeyData('χ'));
		addToBrailleMap(Gpsi, new KeyData('ψ'));
		addToBrailleMap(Gomega, new KeyData('ω'));
		addToBrailleMap(GALPHA, new KeyData('Α'));
		addToBrailleMap(GBETA, new KeyData('Β'));
		addToBrailleMap(GGAMMA, new KeyData('Γ'));
		addToBrailleMap(GDELTA, new KeyData('Δ'));
		addToBrailleMap(GEPSILON, new KeyData('Ε'));
		addToBrailleMap(GZETA, new KeyData('Ζ'));
		addToBrailleMap(GETA, new KeyData('Η'));
		addToBrailleMap(GTHETA, new KeyData('Θ'));
		addToBrailleMap(GIOTA, new KeyData('Ι'));
		addToBrailleMap(GKAPPA, new KeyData('Κ'));
		addToBrailleMap(GLAMBDA, new KeyData('Λ'));
		addToBrailleMap(GMU, new KeyData('Μ'));
		addToBrailleMap(GNU, new KeyData('Ν'));
		addToBrailleMap(GXI, new KeyData('Ξ'));
		addToBrailleMap(GOMICRON, new KeyData('Ο'));
		addToBrailleMap(GPI, new KeyData('Π'));
		addToBrailleMap(GRHO, new KeyData('Ρ'));
		addToBrailleMap(GSIGMA, new KeyData('Σ'));
		addToBrailleMap(GTAU, new KeyData('Τ'));
		addToBrailleMap(GUPSILON, new KeyData('Υ'));
		addToBrailleMap(GPHI, new KeyData('Φ'));
		addToBrailleMap(GCHI, new KeyData('Χ'));
		addToBrailleMap(GPSI, new KeyData('Ψ'));
		addToBrailleMap(GOMEGA, new KeyData('Ω'));


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


	public static final int WHITESPACE = 0;

	// SOME COMMON KEYDATA
	public static final KeyData KD_BACKSPACE = new KeyData('\b', KeyEvent.VK_BACK_SPACE);
	public static final KeyData KD_ENTER = new KeyData('\n', KeyEvent.VK_ENTER);
	public static final KeyData KD_SPACE = new KeyData(' ', KeyEvent.VK_SPACE);
	public static final KeyData KD_WHITESPACE = new KeyData(' ', WHITESPACE);
	public static final KeyData[] KD_WHITESPACES = { KD_SPACE, KD_ENTER };

	// STANDARD ALPHABET KEYDATA
	public static final KeyData KD_Aa = new KeyData('a');
	public static final KeyData KD_Ab = new KeyData('b');
	public static final KeyData KD_Ac = new KeyData('c');
	public static final KeyData KD_Ad = new KeyData('d');
	public static final KeyData KD_Ae = new KeyData('e');
	public static final KeyData KD_Af = new KeyData('f');
	public static final KeyData KD_Ag = new KeyData('g');
	public static final KeyData KD_Ah = new KeyData('h');
	public static final KeyData KD_Ai = new KeyData('i');
	public static final KeyData KD_Aj = new KeyData('j');
	public static final KeyData KD_Ak = new KeyData('k');
	public static final KeyData KD_Al = new KeyData('l');
	public static final KeyData KD_Am = new KeyData('m');
	public static final KeyData KD_An = new KeyData('n');
	public static final KeyData KD_Ao = new KeyData('o');
	public static final KeyData KD_Ap = new KeyData('p');
	public static final KeyData KD_Aq = new KeyData('q');
	public static final KeyData KD_Ar = new KeyData('r');
	public static final KeyData KD_As = new KeyData('s');
	public static final KeyData KD_At = new KeyData('t');
	public static final KeyData KD_Au = new KeyData('u');
	public static final KeyData KD_Av = new KeyData('v');
	public static final KeyData KD_Aw = new KeyData('w');
	public static final KeyData KD_Ax = new KeyData('x');
	public static final KeyData KD_Ay = new KeyData('y');
	public static final KeyData KD_Az = new KeyData('z');
	public static final KeyData KD_AA = new KeyData('A', true);
	public static final KeyData KD_AB = new KeyData('B', true);
	public static final KeyData KD_AC = new KeyData('C', true);
	public static final KeyData KD_AD = new KeyData('D', true);
	public static final KeyData KD_AE = new KeyData('E', true);
	public static final KeyData KD_AF = new KeyData('F', true);
	public static final KeyData KD_AG = new KeyData('G', true);
	public static final KeyData KD_AH = new KeyData('H', true);
	public static final KeyData KD_AI = new KeyData('I', true);
	public static final KeyData KD_AJ = new KeyData('J', true);
	public static final KeyData KD_AK = new KeyData('K', true);
	public static final KeyData KD_AL = new KeyData('L', true);
	public static final KeyData KD_AM = new KeyData('M', true);
	public static final KeyData KD_AN = new KeyData('N', true);
	public static final KeyData KD_AO = new KeyData('O', true);
	public static final KeyData KD_AP = new KeyData('P', true);
	public static final KeyData KD_AQ = new KeyData('Q', true);
	public static final KeyData KD_AR = new KeyData('R', true);
	public static final KeyData KD_AS = new KeyData('S', true);
	public static final KeyData KD_AT = new KeyData('T', true);
	public static final KeyData KD_AU = new KeyData('U', true);
	public static final KeyData KD_AV = new KeyData('V', true);
	public static final KeyData KD_AW = new KeyData('W', true);
	public static final KeyData KD_AX = new KeyData('X', true);
	public static final KeyData KD_AY = new KeyData('Y', true);
	public static final KeyData KD_AZ = new KeyData('Z', true);
	public static final KeyData[] KD_Alower = join(KD_Aa, KD_Ab, KD_Ac, KD_Ad, KD_Ae, KD_Af, KD_Ag, KD_Ah, KD_Ai, KD_Aj, KD_Ak, KD_Al, KD_Am,
												   KD_An, KD_Ao, KD_Ap, KD_Aq, KD_Ar, KD_As, KD_At, KD_Au, KD_Av, KD_Aw, KD_Ax, KD_Ay, KD_Az);
	public static final KeyData[] KD_AUPPER = join(KD_AA, KD_AB, KD_AC, KD_AD, KD_AE, KD_AF, KD_AG, KD_AH, KD_AI, KD_AJ, KD_AK, KD_AL, KD_AM,
												   KD_AN, KD_AO, KD_AP, KD_AQ, KD_AR, KD_AS, KD_AT, KD_AU, KD_AV, KD_AW, KD_AX, KD_AY, KD_AZ);

	// COMBING CHARS KEYDATA
	public static final KeyData KD_DIAERESIS = new KeyData('\u0308');
	public static final KeyData KD_SOLIDUS = new KeyData('\u0338');
	public static final KeyData KD_STRIKETHROUGH = new KeyData('\u0336');
	public static final KeyData[] KD_COMBINING = { KD_DIAERESIS, KD_SOLIDUS, KD_STRIKETHROUGH };

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
	public static final int[] SHIFT8_32 = join(SHIFT8, SHIFT32);
	public static final int SHIFT = SHIFT32;
	public static final int GRADE1 = SHIFT48;
	public static final int[] MODIFIERS = join(DIGIT, SHIFT8, SHIFT16, SHIFT24, SHIFT32, SHIFT40, SHIFT48, SHIFT56);
	public static final int SPACE = -KeyEvent.VK_SPACE;
	public static final int[] WHITESPACES = join(SPACE, ENTER);  // Ordered by most used.
	public static final int GROUP_OPEN = 35; // same as N2
	public static final int GROUP_CLOSE = 28;

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
	public static final int[] AA = join(SHIFT, Aa);
	public static final int[] AB = join(SHIFT, Ab);
	public static final int[] AC = join(SHIFT, Ac);
	public static final int[] AD = join(SHIFT, Ad);
	public static final int[] AE = join(SHIFT, Ae);
	public static final int[] AF = join(SHIFT, Af);
	public static final int[] AG = join(SHIFT, Ag);
	public static final int[] AH = join(SHIFT, Ah);
	public static final int[] AI = join(SHIFT, Ai);
	public static final int[] AJ = join(SHIFT, Aj);
	public static final int[] AK = join(SHIFT, Ak);
	public static final int[] AL = join(SHIFT, Al);
	public static final int[] AM = join(SHIFT, Am);
	public static final int[] AN = join(SHIFT, An);
	public static final int[] AO = join(SHIFT, Ao);
	public static final int[] AP = join(SHIFT, Ap);
	public static final int[] AQ = join(SHIFT, Aq);
	public static final int[] AR = join(SHIFT, Ar);
	public static final int[] AS = join(SHIFT, As);
	public static final int[] AT = join(SHIFT, At);
	public static final int[] AU = join(SHIFT, Au);
	public static final int[] AV = join(SHIFT, Av);
	public static final int[] AW = join(SHIFT, Aw);
	public static final int[] AX = join(SHIFT, Ax);
	public static final int[] AY = join(SHIFT, Ay);
	public static final int[] AZ = join(SHIFT, Az);
	public static final int[] Alower = join(Aa, Ab, Ac, Ad, Ae, Af, Ag, Ah, Ai, Aj, Ak, Al, Am,
											An, Ao, Ap, Aq, Ar, As, At, Au, Av, Aw, Ax, Ay, Az);

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
	public static final int[] NATURAL = join(DIGIT, N1); // No Arial char
	public static final int[] FLAT = join(DIGIT, GROUP_OPEN); // No Arial char
	public static final int[] SHARP = join(DIGIT, 41);
	// SIMPLE PUNCTUATION
	public static final int APOSTROPHE = 4;
	public static final int COLON = 18;
	public static final int COMMA = 2;
	public static final int EXCLAMATION = 22;
	public static final int FULLSTOP = 50;
	public static final int HYPHEN = 36;
	public static final int PRIME = 54;
	public static final int QUESTION = 38;
	public static final int SEMICOLON = 6;

	// COMPLEX PUNCTUATION
	public static final int[] DOUBLE_PRIME = join(PRIME, PRIME);

	// GROUP PUNCTUATION
	public static final int[] ANGLE_BRACKET_OPEN = join(SHIFT8, GROUP_OPEN);
	public static final int[] ANGLE_BRACKET_CLOSE = join(SHIFT8, GROUP_CLOSE);
	public static final int[] CURLY_BRACKET_OPEN = join(SHIFT56, GROUP_OPEN);
	public static final int[] CURLY_BRACKET_CLOSE = join(SHIFT56, GROUP_CLOSE);
	public static final int[] ROUND_BRACKET_OPEN = join(SHIFT16, GROUP_OPEN);
	public static final int[] ROUND_BRACKET_CLOSE = join(SHIFT16, GROUP_CLOSE);
	public static final int[] SQUARE_BRACKET_OPEN = join(SHIFT40, GROUP_OPEN);
	public static final int[] SQUARE_BRACKET_CLOSE = join(SHIFT40, GROUP_CLOSE);

	// SHIFT 8 / CURRENCY
	public static final int[] AMPERSAND = join(SHIFT8, 47);
	public static final int[] AT_SIGN = join(SHIFT8, Aa);
	public static final int[] CARET = join(SHIFT8, 34);
	public static final int[] CENT = join(SHIFT8, Ac);
	public static final int[] DOLLAR = join(SHIFT8, As);
	public static final int[] EURO = join(SHIFT8, Ae);
	public static final int[] FRANC = join(SHIFT8, Af);
	public static final int[] GBP = join(SHIFT8, Al);
	public static final int[] GRREATER_THAN = ANGLE_BRACKET_CLOSE;
	public static final int[] LESS_THAN = ANGLE_BRACKET_OPEN;
	public static final int[] NAIRA = join(SHIFT8, An);
	public static final int[] TILDE = join(SHIFT8, 20);
	public static final int[] YEN = join(SHIFT8, Ay);
	// SHIFT 8 - COMBINING CHARACTERS
	public static final int[] SOLIDUS = join(SHIFT8, N1);
	public static final int[] STRIKETHROUGH = join(SHIFT8, COLON);
	// SHIFT 8 -> SHIFT 32
	public static final int[] DAGGER = join(SHIFT8_32, N4);
	public static final int[] DOUBLE_DAGGER = join(SHIFT8_32, 59);

	// SHIFT 16 / MATHS
	public static final int[] ASTERISK = join(SHIFT16, 20);
	public static final int[] DITTO = join(SHIFT16, 2);
	public static final int[] DIVIDE = join(SHIFT16, 12);
	public static final int[] EQUALS = join(SHIFT16, PRIME);
	public static final int[] MINUS = join(SHIFT16, HYPHEN);
	public static final int[] MULTIPLY = join(SHIFT16, QUESTION);
	public static final int[] PLUS = join(SHIFT16, EXCLAMATION);

	// SHIFT 24
	public static final int[] COPYRIGHT = join(SHIFT24, Ac);
	public static final int[] DEGREES = join(SHIFT24, Aj);
	public static final int[] PARAGRAPH = join(SHIFT24, Ap);
	public static final int[] REGISTERED = join(SHIFT24, Ar);
	public static final int[] SECTION = join(SHIFT24, As);
	public static final int[] TRADEMARK = join(SHIFT24, At);
	public static final int[] FEMALE = join(SHIFT24, Ax);
	public static final int[] MALE = join(SHIFT24, Ay);
	// SHIFT 24 - COMBINING CHARACTERS
	public static final int[] DIAERESIS = join(SHIFT24, COLON);

	// SHIFT 40
	public static final int[] PERCENT = join(SHIFT40, 52);
	public static final int[] UNDERSCORE = join(SHIFT40, HYPHEN);

	// SHIFT 56
	public static final int[] BACK_SLASH = join(SHIFT56, N1);
	public static final int[] BULLET = join(SHIFT56, FULLSTOP);
	public static final int[] FORWARD_SLASH = join(SHIFT56, 12);
	public static final int[] NUMBER = join(SHIFT56, N4);

	private static final int[][] COMBINING = { DIAERESIS, SOLIDUS, STRIKETHROUGH };

	// GREEK ALPHABET
	private static final int[] Galpha = join(SHIFT40, Aa);
	private static final int[] Gbeta = join(SHIFT40, Ab);
	private static final int[] Ggamma = join(SHIFT40, Ag);
	private static final int[] Gdelta = join(SHIFT40, Ad);
	private static final int[] Gepsilon = join(SHIFT40, Ae);
	private static final int[] Gzeta = join(SHIFT40, Az);
	private static final int[] Geta = join(SHIFT40, N5);
	private static final int[] Gtheta = join(SHIFT40, N4);
	private static final int[] Giota = join(SHIFT40, Ai);
	private static final int[] Gkappa = join(SHIFT40, Ak);
	private static final int[] Glambda = join(SHIFT40, Al);
	private static final int[] Gmu = join(SHIFT40, Am);
	private static final int[] Gnu = join(SHIFT40, An);
	private static final int[] Gxi = join(SHIFT40, Ax);
	private static final int[] Gomicron = join(SHIFT40, Ao);
	private static final int[] Gpi = join(SHIFT40, Ap);
	private static final int[] Grho = join(SHIFT40, Ar);
	private static final int[] Gsigma = join(SHIFT40, As);
	private static final int[] Gtau = join(SHIFT40, At);
	private static final int[] Gupsilon = join(SHIFT40, Au);
	private static final int[] Gphi = join(SHIFT40, Af);
	private static final int[] Gchi = join(SHIFT40, 47);
	private static final int[] Gpsi = join(SHIFT40, Ay);
	private static final int[] Gomega = join(SHIFT40, Aw);
	private static final int[] GALPHA = join(SHIFT, Galpha);
	private static final int[] GBETA = join(SHIFT, Gbeta);
	private static final int[] GGAMMA = join(SHIFT, Ggamma);
	private static final int[] GDELTA = join(SHIFT, Gdelta);
	private static final int[] GEPSILON = join(SHIFT, Gepsilon);
	private static final int[] GZETA = join(SHIFT, Gzeta);
	private static final int[] GETA = join(SHIFT, Geta);
	private static final int[] GTHETA = join(SHIFT, Gtheta);
	private static final int[] GIOTA = join(SHIFT, Giota);
	private static final int[] GKAPPA = join(SHIFT, Gkappa);
	private static final int[] GLAMBDA = join(SHIFT, Glambda);
	private static final int[] GMU = join(SHIFT, Gmu);
	private static final int[] GNU = join(SHIFT, Gnu);
	private static final int[] GXI = join(SHIFT, Gxi);
	private static final int[] GOMICRON = join(SHIFT, Gomicron);
	private static final int[] GPI = join(SHIFT, Gpi);
	private static final int[] GRHO = join(SHIFT, Grho);
	private static final int[] GSIGMA = join(SHIFT, Gsigma);
	private static final int[] GTAU = join(SHIFT, Gtau);
	private static final int[] GUPSILON = join(SHIFT, Gupsilon);
	private static final int[] GPHI = join(SHIFT, Gphi);
	private static final int[] GCHI = join(SHIFT, Gchi);
	private static final int[] GPSI = join(SHIFT, Gpsi);
	private static final int[] GOMEGA = join(SHIFT, Gomega);
}