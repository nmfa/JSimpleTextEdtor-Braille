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
	public static final int OVERFLOWS = 1;
	public static final int MODIFIER = 2;
	public static final int LIGATURE = 4;
	public static final int WHITESPACE = 8;
	public static final int CHARACTER = 16;
	public static final int ALPHABET = 32;
	public static final int STRING = 64;
	public static final int STANDALONE = 128;
	public static final int FINAL = 120;
	public static final int CONCRETE = 126;

	public ArrayList<KeyData> keyData;
	public HashMap<Integer, MapData> map;
	public int type;
    Logger log = Logger.getLogger("MapData");

	MapData() {
		this.keyData = null;
		this.map = new HashMap<Integer, MapData>();
		this.type = OVERFLOWS;
	}

	MapData(KeyData kd, int tp) {
		this(new ArrayList<KeyData>(Arrays.asList(kd)), tp);
	}

	MapData(KeyData[] kd, int tp) {
		this(new ArrayList<KeyData>(Arrays.asList(kd)), tp);
	}

	MapData(ArrayList<KeyData> kd, int tp) {
		this.keyData = kd;
		this.map = null;
		this.type = tp;
		if (type == OVERFLOWS) /*ONLY*/ {
			log.severe("MAPDATA CONTAINING KEYDATA SHOULD HAVE A CONCRETE TYPE");
		}
	}

	public void addMap() {
		if (map == null) map = new HashMap<Integer, MapData>();
		type = type | OVERFLOWS;
	}

	public boolean overflows() {
		return (type & OVERFLOWS) > 0;
	}

	public boolean isConcrete() {
		return (type & CONCRETE) > 0;
	}

	public boolean isFinal() {
		return (type & FINAL) > 0;
	}

	public boolean isModifier() {
		return (type & MODIFIER) > 0;
	}

	public boolean isAlphabet() {
		return (type & ALPHABET) > 0;
	}

	public boolean isOverflow() {
		return (type & OVERFLOWS) > 0;
	}

	public boolean isWhitespace() {
		return (type & WHITESPACE) > 0;
	}

	public boolean isStandAloneMarker() {
		return (type & STANDALONE) > 0;
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
	private KeyData lastKeyDataSent = null;
	private int wordLength = 0;
	private int shift = 0; // 0 = no shift, 1 = normal, 2 = word, 3 = Caps Lock
	private int shift40 = 0;
	private int digit = 0; // 0 = not a digit, 1 = normal, 2 = word, 3 = Num Lock
	//private int pinCodeOverflow = 0; // Depth of overflow when doing prime, double prime type examples.

    TextAreaBraille() {
        super();
        populateBrailleMaps();
		// Initialise as if we've just had whitespace, arbitrarily ENTER.
		//currentPinCodesList.add(ENTER);
		//pinCodeOverflow = 1;
		lastKeyDataSent = KD_ENTER;
    }


    @Override
    protected void processKeyEvent(KeyEvent e) {
		// Not setting lastKeyDown here, as it'll make the onKeyDown and onKeyUp logic consistent for when it is
		// moved to an API which has those functions, rather than this single API function.
		if (e.getID() == KeyEvent.KEY_PRESSED) {
			onKeyDown(e);
        } else if (e.getID() == KeyEvent.KEY_RELEASED) {
			log.info("PROCESSING EVENT: " + currentPinCode + ", " + currentPinCodesList.toString() + ", " + keyPinMap.getOrDefault(e.getKeyCode(), 0));
			onKeyUp(e);
			log.info("PROCESSED: " + currentPinCode + ", " + currentPinCodesList.toString());
        } else {
            // Ignore
        }
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
			log.info("PIN CODE SEQUENCE: " + currentPinCodesList.toString());
			MapData md = parsePinCodes();
			if (md == null) {
				int originalPinCodesListLength = currentPinCodesList.size();
				// This should only ever recurse once.
				currentPinCodesList.clear();
				//pinCodeOverflow = 0;
				if (shift == 1) shift = 0;
				if (shift40 == 1) shift40 = 0;
				if (digit == 1) digit = 0;
				if (shift > 0) currentPinCodesList.add(SHIFT);
				if (shift40 > 0) currentPinCodesList.add(SHIFT40);
				if (digit > 0) currentPinCodesList.add(DIGIT);
				// A good proxy for ensuring we don't wind up retrying the same failed code sequence.
				if (currentPinCodesList.size() != originalPinCodesListLength - 1) {
					onKeyUp(e);
				} else {
					currentPinCode = ~(~currentPinCode | pin);
					lastKeyDown = false;
				}
				return;
			}
			if (md.keyData != null) {
				// Send character sequence.
				if (md.isAlphabet()) {
					int aCase = (shift > 0) ? UPPER : LOWER;
					sendKeyEvents(e.getComponent(), e.getWhen(), md.keyData.get(aCase));
					wordLength++;
				} else if (md.isFinal()) {
					sendKeyEvents(e.getComponent(), e.getWhen(), md.keyData);
					// This will not be quite accurate for many modified letters,
					// but they can't be part of contractions, and that is the purpose
					// of wordLength.
					wordLength += md.keyData.size();
				}
			}
			// We may possibly be doing a prime, double prime situation.
//			if (md.isOverflow()) {
//				// Get next pin code.
//				pinCodeOverflow = currentPinCodesList.size();
//			}

			//if (pinCodeOverflow == 0) {
			if (!md.isOverflow()) {
				// Reset the pin code sequence, as one way or another it is done with.
				currentPinCodesList.clear();
				//pinCodeOverflow = 0;
			}
			// Unless WHITESPACE which is special case which terminate and start anoth overflow.
			// I don't think any other character has this property.
			// But this needs to happen regardless of the current overflow status.
			// There shouldn't be any situations where ENTER will be in an existing oveeflow
			// when ENTER is the current pin code.
			//if (isWhitespace(currentPinCode) &&
//			if (md.isWhitespace() &&
//				(currentPinCodesList.size() == 0 ||
//				 (currentPinCodesList.size() > 0 && !isWhitespace(currentPinCodesList.get(0))))) {
//				  currentPinCodesList.add(0, currentPinCode);
//				pinCodeOverflow++;
//			}

			// Deal with word locks of shift and digit
			//if (isWhitespace(currentPinCode)) {
			if (md.isWhitespace()) {
				if (shift < 3) shift = 0;
				if (shift40 < 3) shift40 = 0;
				if (digit < 3) digit = 0;
			}

			//if (pinCodeOverflow == 0) {
			if (!md.isOverflow()) {
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


	private MapData __getMapDataFromBrailleMap(int[] pcIndex) {
		return __getMapDataFromMap(pcIndex, brailleMap);
	}

	private MapData __getMapDataFromMap(int[] pcIndex, HashMap<Integer, MapData> map) {
		MapData md = null;
		while (pcIndex[0] < currentPinCodesList.size() && map != null) {
			md = map.get(currentPinCodesList.get(pcIndex[0]));
			pcIndex[0]++;
			if (md == null) return null;
			if (md.isConcrete()) return md;
			map = md.map;
		}
		return md;
	}

	private MapData getModifiedAlphabet(MapData md, Integer pinCode) {
		if (md.map == null) return null;
		return md.map.get(pinCode);
	}

	private MapData parsePinCodes() {
		int pinCodeCount = currentPinCodesList.size();
		// I really want to pass and update this in a couple of helper functions.
		// For such limited use this is probably the most efficient way, if a little ugly.
		int[] pcIndex = {0};
		int aCase = LOWER;
		MapData mdModifier = null;

		// SHIFT always first code
		if (currentPinCodesList.get(pcIndex[0]) == SHIFT) {
			if (pinCodeCount == 1) return new MapData();
			aCase = UPPER;
			pcIndex[0]++;
		}

		MapData md = null;
		while (pcIndex[0] < pinCodeCount) {
			md = __getMapDataFromBrailleMap(pcIndex);
			if (md == null) return null;
			if (md.isFinal()) { // Not just a modifier
				// In the rare case where this is also an overflow, check further.
				// The next code should always be concrete, and so far no double overflows of this sort.
				if (md.isOverflow() && pcIndex[0] < pinCodeCount) {
					MapData mdOverflow = __getMapDataFromMap(pcIndex, md.map);
					if (mdOverflow != null) {
						return mdOverflow;
					} else {
						// Otherwise we need to start again with the final pincode.
						Integer finalPinCode = currentPinCodesList.get(pinCodeCount-1);
						currentPinCodesList.clear();
						if (shift > 0) currentPinCodesList.add(SHIFT);
						if (shift40 > 0) currentPinCodesList.add(SHIFT40);
						if (digit > 0) currentPinCodesList.add(DIGIT);
						currentPinCodesList.add(finalPinCode);
						return parsePinCodes();
					}
				}
				if (md.isAlphabet()) {
					// Apply any modifier
					if (mdModifier != null) {
						MapData mdModified =
							getModifiedAlphabet(mdModifier, currentPinCodesList.get(pinCodeCount-1));
						if (mdModified == null) {
							return new MapData(join(md.keyData.get(aCase), mdModifier.keyData.get(0)), MapData.CHARACTER);
						} else {
							// On rare combinations there is only a nomralised char for one case.
							if (mdModified.keyData.get(aCase).keyCode == 0) {
								return new MapData(join(md.keyData.get(aCase), mdModifier.keyData.get(0)), MapData.CHARACTER); 
							} else {
								return mdModified;
							}
						}
					} else {
						return md;
					}
				} else { // WHITESPACE, CHARACTER, STRING, none of which can be modified.
					return md;
				}
			}
			if (md.isModifier()) {
				mdModifier = md;
			}
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
		lastKeyDataSent = keyData;
	}


	private void addAlphabetsToBrailleMap() {
		for (Integer code: ALPHABET.keySet()) {
			Integer[] codes = {code};
			addToBrailleMap(codes, MapData.ALPHABET, ALPHABET.get(code));
		}
		for (Integer[] codes: GREEK.keySet()) {
			addToBrailleMap(codes, MapData.ALPHABET, GREEK.get(codes));
		}
	}

	private void addCombiningCharsToBrailleMap() {
		for (Integer[] mCodes: MODIFIERS.keySet()) {
			KeyData[] mKeyData = MODIFIERS.get(mCodes);
			addToBrailleMap(mCodes, MapData.MODIFIER, mKeyData);
			for (int aCode: ALPHABET.keySet()) {
				// THe expectation is that these will be pairs of lower and upper case modified characters.
				boolean modifiedPair = false;
				KeyData[] pair = new KeyData[2];
				KeyData[] aKeyData = ALPHABET.get(aCode);
				String combination = String.valueOf(aKeyData[LOWER].keyChar) + String.valueOf(mKeyData[0].keyChar);
				char keyChar = Normalizer.normalize(combination, Form.NFC).charAt(0);
				if (keyChar != aKeyData[LOWER].keyChar) {
					pair[LOWER] = new KeyData(keyChar);
					modifiedPair = true;
				}
				combination = String.valueOf(aKeyData[UPPER].keyChar) + String.valueOf(mKeyData[0].keyChar);
				keyChar = Normalizer.normalize(combination, Form.NFC).charAt(0);
				if (keyChar != aKeyData[UPPER].keyChar) {
					pair[UPPER] = new KeyData(keyChar);
					if (!modifiedPair) {
						pair[LOWER] = new KeyData(' ', 0);
						log.warning("UPPER MODIFIED CHARACTER, BUT NO LOWER: " + aKeyData[LOWER].keyChar + String.format("\\u%04x", (int) mKeyData[0].keyChar));
					}
					modifiedPair = true;
				} else if (modifiedPair) {
					pair[UPPER] = new KeyData(' ', 0);
					log.warning("LOWER MODIFIED CHARACTER, BUT NO UPPER: " + aKeyData[UPPER].keyChar + String.format("\\u%04x", (int) mKeyData[0].keyChar));
				}
				if (modifiedPair) {
					addToBrailleMap(join(mCodes, aCode), MapData.ALPHABET, pair);
				}
			}
		}
	}

	private void addCharToBrailleMap(Integer[] pinCodes, KeyData... keyData) {
		addToBrailleMap(pinCodes, MapData.CHARACTER, keyData);
	}

	private void addCharToBrailleMap(Integer pinCode, KeyData... keyData) {
		Integer[] pinCodes = {pinCode};
		addCharToBrailleMap(pinCodes, keyData);
	}

	private void addStandAloneToBrailleMap(Integer[] pinCodes, KeyData... keyData) {
		addToBrailleMap(pinCodes, MapData.CHARACTER | MapData.STANDALONE, keyData);
	}
	private void addStandAloneToBrailleMap(Integer pinCode, KeyData... keyData) {
		Integer[] pinCodes = {pinCode};
		addStandAloneToBrailleMap(pinCodes, keyData);
	}


	private void addWhitespaceToBrailleMap(Integer pinCode, KeyData... keyData) {
		Integer[] pinCodes = {pinCode};
		addToBrailleMap(pinCodes, MapData.WHITESPACE | MapData.STANDALONE, keyData);
	}

	private void addToBrailleMap(Integer[] pinCodes, int type, KeyData... keyData) {
		addToBrailleMap(pinCodes, type, 0, new ArrayList<KeyData>(Arrays.asList(keyData)), brailleMap);
	}

	private void addToBrailleMap(Integer[] pinCodes, int type, int pcIndex, ArrayList<KeyData> keyData, HashMap<Integer, MapData> map) {
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
				addToBrailleMap(pinCodes, type, pcIndex + 1, keyData, mapData.map);
			}
		} else {
			if (pcIndex + 1 == pinCodes.length) {
				MapData md = new MapData(keyData, type);
				map.put(pinCodes[pcIndex], md);
			} else {
				MapData md = new MapData();
				map.put(pinCodes[pcIndex], md);
				addToBrailleMap(pinCodes, type, pcIndex + 1, keyData, md.map);
			}
		}
	}


	@SafeVarargs
	private static <T> T[] join(T... items) {
		return Arrays.copyOf(items, items.length);
	}

	private static Integer[] join(Integer[] arr, Integer... items) {
		Integer[] newArr = Arrays.copyOf(arr, arr.length + items.length);
		System.arraycopy(items, 0, newArr, arr.length, items.length);
		return newArr;
	}


    private void  populateBrailleMaps() {
		// ENGLISH ALPHABET
		addAlphabetsToBrailleMap();
		addCombiningCharsToBrailleMap();

		addWhitespaceToBrailleMap(ENTER, KD_ENTER);
		addWhitespaceToBrailleMap(SPACE, KD_SPACE);
 	
		// NUMBERS
		// COMPUTER NOTATION
		for (Integer code: NUMBERS.keySet()) {
			addCharToBrailleMap(code, NUMBERS.get(code));
		}
		// WITH DIGIT PREFIX: sets aren't ordered.
        addCharToBrailleMap(D0, KD_0);
        addCharToBrailleMap(D1, KD_1);
        addCharToBrailleMap(D2, KD_2);
        addCharToBrailleMap(D3, KD_3);
        addCharToBrailleMap(D4, KD_4);
        addCharToBrailleMap(D5, KD_5);
        addCharToBrailleMap(D6, KD_6);
        addCharToBrailleMap(D7, KD_7);
        addCharToBrailleMap(D8, KD_8);
        addCharToBrailleMap(D9, KD_9);

		// MUSIC
        addCharToBrailleMap(SHARP, new KeyData('♯'));

		// SIMPLE PUNCTUATION
		addStandAloneToBrailleMap(APOSTROPHE, new KeyData('\''));
		addStandAloneToBrailleMap(BACK_SLASH, new KeyData('\\', KeyEvent.VK_BACK_SLASH));
		addStandAloneToBrailleMap(BULLET, new KeyData('•'));
		addStandAloneToBrailleMap(CARET, new KeyData('^', true));
		addStandAloneToBrailleMap(COLON, new KeyData(':', KeyEvent.VK_COLON, true));
		addStandAloneToBrailleMap(COMMA, new KeyData(',', KeyEvent.VK_COMMA));
		addStandAloneToBrailleMap(EXCLAMATION, new KeyData('!', KeyEvent.VK_EXCLAMATION_MARK, true));
		addStandAloneToBrailleMap(FORWARD_SLASH, new KeyData('/', KeyEvent.VK_SLASH));
		addStandAloneToBrailleMap(FULLSTOP, new KeyData('.', KeyEvent.VK_PERIOD));
		addStandAloneToBrailleMap(HYPHEN, new KeyData('-', KeyEvent.VK_MINUS));
		addStandAloneToBrailleMap(NUMBER, new KeyData('#'));
		addToBrailleMap(join(PRIME), MapData.OVERFLOWS | MapData.CHARACTER | MapData.STANDALONE, new KeyData('′'));
		addStandAloneToBrailleMap(QUESTION, new KeyData('?', true));
		//addCharToBrailleMap(QUOTE, new KeyData('"', true));
		addStandAloneToBrailleMap(SEMICOLON, new KeyData(';', KeyEvent.VK_SEMICOLON));
		addStandAloneToBrailleMap(TILDE, new KeyData('~', true));
		addCharToBrailleMap(UNDERSCORE, new KeyData('_', KeyEvent.VK_UNDERSCORE, true));

		// COMPLEX PUNCTUATION
		addStandAloneToBrailleMap(DOUBLE_PRIME, KD_BACKSPACE, new KeyData('″'));

		// GROUP PUNCTUATION
		addStandAloneToBrailleMap(ANGLE_BRACKET_OPEN, new KeyData('<', true));
		addStandAloneToBrailleMap(ANGLE_BRACKET_CLOSE, new KeyData('>', true));
		addStandAloneToBrailleMap(CURLY_BRACKET_OPEN, new KeyData('{', true));
		addStandAloneToBrailleMap(CURLY_BRACKET_CLOSE, new KeyData('}', true));
		addStandAloneToBrailleMap(ROUND_BRACKET_OPEN, new KeyData('(', true));
		addStandAloneToBrailleMap(ROUND_BRACKET_CLOSE, new KeyData(')', true));
		addStandAloneToBrailleMap(SQUARE_BRACKET_OPEN, new KeyData('['));
		addStandAloneToBrailleMap(SQUARE_BRACKET_CLOSE, new KeyData(']'));

		// CURRENCY
		addCharToBrailleMap(CENT, new KeyData('¢'));
		addCharToBrailleMap(DOLLAR, new KeyData('$', KeyEvent.VK_DOLLAR, true));
		addCharToBrailleMap(EURO, new KeyData('€'));
		addCharToBrailleMap(FRANC, new KeyData('₣'));
		addCharToBrailleMap(GBP, new KeyData('£', true));
		addCharToBrailleMap(NAIRA, new KeyData('₦'));
		addCharToBrailleMap(YEN, new KeyData('¥'));

		// MATHS
		addStandAloneToBrailleMap(ASTERISK, new KeyData('*', KeyEvent.VK_ASTERISK, true));
		addStandAloneToBrailleMap(DITTO, new KeyData('"', true));
		addStandAloneToBrailleMap(DIVIDE, new KeyData('÷'));
		addStandAloneToBrailleMap(EQUALS, new KeyData('=', KeyEvent.VK_EQUALS));
		addStandAloneToBrailleMap(MINUS, new KeyData('-', KeyEvent.VK_MINUS));
		addStandAloneToBrailleMap(MULTIPLY, new KeyData('×'));
		addStandAloneToBrailleMap(PERCENT, new KeyData('\u0025', 37));
		addStandAloneToBrailleMap(PLUS, new KeyData('+', KeyEvent.VK_PLUS, true));
	
		// SYMBOLS
		addStandAloneToBrailleMap(AMPERSAND, new KeyData('&', true));
		addStandAloneToBrailleMap(AT_SIGN, new KeyData('@', true));
		addStandAloneToBrailleMap(COPYRIGHT, new KeyData('©'));
		addStandAloneToBrailleMap(DAGGER, new KeyData('†'));
		addStandAloneToBrailleMap(DOUBLE_DAGGER, new KeyData('‡'));
		addStandAloneToBrailleMap(DEGREES, new KeyData('°'));
		addCharToBrailleMap(FEMALE, new KeyData('♀'));
		addCharToBrailleMap(MALE, new KeyData('♂'));
		addStandAloneToBrailleMap(PARAGRAPH, new KeyData('¶'));
		addStandAloneToBrailleMap(REGISTERED, new KeyData('®'));
		addStandAloneToBrailleMap(SECTION, new KeyData('§'));
		addStandAloneToBrailleMap(TRADEMARK, new KeyData('™'));

		// ASCII CHARACTERS. Stored under the negative of their keycode.
        addWhitespaceToBrailleMap(Integer.valueOf(-KeyEvent.VK_ENTER), KD_ENTER);
        //addWhitespaceToBrailleMap(Integer.valueOf(-KeyEvent.VK_SPACE), KD_SPACE);
    	addCharToBrailleMap(Integer.valueOf(-KeyEvent.VK_BACK_SPACE), KD_BACKSPACE);

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
	public static final int WILDCARD = 63; // Same as N0

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

	// HREEK ALPHABET
	public static final KeyData KD_Galpha = new KeyData('α');
	public static final KeyData KD_Gbeta = new KeyData('β');
	public static final KeyData KD_Ggamma = new KeyData('γ');
	public static final KeyData KD_Gdelta = new KeyData('δ');
	public static final KeyData KD_Gepsilon = new KeyData('ε');
	public static final KeyData KD_Gzeta = new KeyData('ζ');
	public static final KeyData KD_Geta = new KeyData('η');
	public static final KeyData KD_Gtheta = new KeyData('θ');
	public static final KeyData KD_Giota = new KeyData('ι');
	public static final KeyData KD_Gkappa = new KeyData('κ');
	public static final KeyData KD_Glambda = new KeyData('λ');
	public static final KeyData KD_Gmu = new KeyData('μ');
	public static final KeyData KD_Gnu = new KeyData('ν');
	public static final KeyData KD_Gxi = new KeyData('ξ');
	public static final KeyData KD_Gomicron = new KeyData('ο');
	public static final KeyData KD_Gpi = new KeyData('π');
	public static final KeyData KD_Grho = new KeyData('ρ');
	//final KeyData KD_sigma = new KeyData('σ');
	public static final KeyData KD_Gsigma = new KeyData('σ');
	//public static final KeyData KD_join(WHITESPACE = Gsigma) = KD_sigma); // To prevent the nexy firing when just the letter σ.
	//public static final KeyData KD_join(Gsigma = WHITESPACE) = KD_BACKSPACE = new KeyData('ς') = KD_WHITESPACE); // Sigma at the end of a word.
	public static final KeyData KD_Gtau = new KeyData('τ');
	public static final KeyData KD_Gupsilon = new KeyData('υ');
	public static final KeyData KD_Gphi = new KeyData('φ');
	public static final KeyData KD_Gchi = new KeyData('χ');
	public static final KeyData KD_Gpsi = new KeyData('ψ');
	public static final KeyData KD_Gomega = new KeyData('ω');
	public static final KeyData KD_GALPHA = new KeyData('Α');
	public static final KeyData KD_GBETA = new KeyData('Β');
	public static final KeyData KD_GGAMMA = new KeyData('Γ');
	public static final KeyData KD_GDELTA = new KeyData('Δ');
	public static final KeyData KD_GEPSILON = new KeyData('Ε');
	public static final KeyData KD_GZETA = new KeyData('Ζ');
	public static final KeyData KD_GETA = new KeyData('Η');
	public static final KeyData KD_GTHETA = new KeyData('Θ');
	public static final KeyData KD_GIOTA = new KeyData('Ι');
	public static final KeyData KD_GKAPPA = new KeyData('Κ');
	public static final KeyData KD_GLAMBDA = new KeyData('Λ');
	public static final KeyData KD_GMU = new KeyData('Μ');
	public static final KeyData KD_GNU = new KeyData('Ν');
	public static final KeyData KD_GXI = new KeyData('Ξ');
	public static final KeyData KD_GOMICRON = new KeyData('Ο');
	public static final KeyData KD_GPI = new KeyData('Π');
	public static final KeyData KD_GRHO = new KeyData('Ρ');
	public static final KeyData KD_GSIGMA = new KeyData('Σ');
	public static final KeyData KD_GTAU = new KeyData('Τ');
	public static final KeyData KD_GUPSILON = new KeyData('Υ');
	public static final KeyData KD_GPHI = new KeyData('Φ');
	public static final KeyData KD_GCHI = new KeyData('Χ');
	public static final KeyData KD_GPSI = new KeyData('Ψ');
	public static final KeyData KD_GOMEGA = new KeyData('Ω');

	// NUMBERS
	public static final KeyData KD_0 = new KeyData('0');
	public static final KeyData KD_1 = new KeyData('1');
	public static final KeyData KD_2 = new KeyData('2');
	public static final KeyData KD_3 = new KeyData('3');
	public static final KeyData KD_4 = new KeyData('4');
	public static final KeyData KD_5 = new KeyData('5');
	public static final KeyData KD_6 = new KeyData('6');
	public static final KeyData KD_7 = new KeyData('7');
	public static final KeyData KD_8 = new KeyData('8');
	public static final KeyData KD_9 = new KeyData('9');

	// COMBING CHARS KEYDATA
	public static final KeyData KD_ACUTE = new KeyData('\u0301');
	public static final KeyData KD_CARON = new KeyData('\u030C');
	public static final KeyData KD_BREVE = new KeyData('\u0306');
	public static final KeyData KD_CEDILLA = new KeyData('\u0327');
	public static final KeyData KD_CIRCUMFLEX = new KeyData('\u0302');
	public static final KeyData KD_DIAERESIS = new KeyData('\u0308');
	public static final KeyData KD_GRAVE = new KeyData('\u0300');
	public static final KeyData KD_MACRON = new KeyData('\u0305');
	public static final KeyData KD_RING = new KeyData('\u030A');
	public static final KeyData KD_SOLIDUS = new KeyData('\u0338');
	public static final KeyData KD_STRIKETHROUGH = new KeyData('\u0336');
	public static final KeyData KD_TILDE_COMB = new KeyData('\u0303');

	// QUALIFIERS
	public static final Integer DIGIT = 60;
	public static final Integer SHIFT8 = 8;
	public static final Integer SHIFT16 = 16;
	public static final Integer SHIFT24 = 24;
	public static final Integer SHIFT32 = 32;
	public static final Integer SHIFT40 = 40;
	public static final Integer SHIFT48 = 48;
	public static final Integer SHIFT56 = 56;
	public static final Integer SHIFT = SHIFT32;
	public static final Integer GRADE1 = SHIFT48;
	public static final Integer[] QUALIFIERS = join(DIGIT, SHIFT8, SHIFT16, SHIFT24, SHIFT32, SHIFT40, SHIFT48, SHIFT56);

	// PINCODES
	// SPECIAL
	public static final Integer ENTER = 128;
	public static final Integer[] SHIFT8_32 = join(SHIFT8, SHIFT32);
	public static final Integer SPACE = -KeyEvent.VK_SPACE;
	public static final Integer[] WHITESPACES = join(SPACE, ENTER);  // Ordered by most used.
	public static final Integer GROUP_OPEN = 35; // same as N2
	public static final Integer GROUP_CLOSE = 28;

	// LETTERS
	public static final Integer Aa = 1;
	public static final Integer Ab = 3;
	public static final Integer Ac = 9;
	public static final Integer Ad = 25;
	public static final Integer Ae = 17;
	public static final Integer Af = 11;
	public static final Integer Ag = 27;
	public static final Integer Ah = 19;
	public static final Integer Ai = 10;
	public static final Integer Aj = 26;
	public static final Integer Ak = 5;
	public static final Integer Al = 7;
	public static final Integer Am = 13;
	public static final Integer An = 29;
	public static final Integer Ao = 21;
	public static final Integer Ap = 15;
	public static final Integer Aq = 31;
	public static final Integer Ar = 23;
	public static final Integer As = 14;
	public static final Integer At = 30;
	public static final Integer Au = 37;
	public static final Integer Av = 39;
	public static final Integer Aw = 58;
	public static final Integer Ax = 45;
	public static final Integer Ay = 61;
	public static final Integer Az = 53;
	public static final HashMap<Integer, KeyData[]> ALPHABET = new HashMap<Integer, KeyData[]>();
	static {
		ALPHABET.put(Aa, new KeyData[] {KD_Aa, KD_AA});
		ALPHABET.put(Ab, new KeyData[] {KD_Ab, KD_AB});
		ALPHABET.put(Ac, new KeyData[] {KD_Ac, KD_AC});
		ALPHABET.put(Ad, new KeyData[] {KD_Ad, KD_AD});
		ALPHABET.put(Ae, new KeyData[] {KD_Ae, KD_AE});
		ALPHABET.put(Af, new KeyData[] {KD_Af, KD_AF});
		ALPHABET.put(Ag, new KeyData[] {KD_Ag, KD_AG});
		ALPHABET.put(Ah, new KeyData[] {KD_Ah, KD_AH});
		ALPHABET.put(Ai, new KeyData[] {KD_Ai, KD_AI});
		ALPHABET.put(Aj, new KeyData[] {KD_Aj, KD_AJ});
		ALPHABET.put(Ak, new KeyData[] {KD_Ak, KD_AK});
		ALPHABET.put(Al, new KeyData[] {KD_Al, KD_AL});
		ALPHABET.put(Am, new KeyData[] {KD_Am, KD_AM});
		ALPHABET.put(An, new KeyData[] {KD_An, KD_AN});
		ALPHABET.put(Ao, new KeyData[] {KD_Ao, KD_AO});
		ALPHABET.put(Ap, new KeyData[] {KD_Ap, KD_AP});
		ALPHABET.put(Aq, new KeyData[] {KD_Aq, KD_AQ});
		ALPHABET.put(Ar, new KeyData[] {KD_Ar, KD_AR});
		ALPHABET.put(As, new KeyData[] {KD_As, KD_AS});
		ALPHABET.put(At, new KeyData[] {KD_At, KD_AT});
		ALPHABET.put(Au, new KeyData[] {KD_Au, KD_AU});
		ALPHABET.put(Av, new KeyData[] {KD_Av, KD_AV});
		ALPHABET.put(Aw, new KeyData[] {KD_Aw, KD_AW});
		ALPHABET.put(Ax, new KeyData[] {KD_Ax, KD_AX});
		ALPHABET.put(Ay, new KeyData[] {KD_Ay, KD_AY});
		ALPHABET.put(Az, new KeyData[] {KD_Az, KD_AZ});
	}
	public static final int LOWER = 0;
	public static final int UPPER = 1;

	// NUMBERS
	public static final Integer N0 = 63;
	public static final Integer N1 = 33;
	public static final Integer N2 = 35;
	public static final Integer N3 = 41;
	public static final Integer N4 = 57;
	public static final Integer N5 = 49;
	public static final Integer N6 = 43;
	public static final Integer N7 = 59;
	public static final Integer N8 = 51;
	public static final Integer N9 = 42;
	public static final Integer[] D1 = join(DIGIT, Aa);
	public static final Integer[] D2 = join(DIGIT, Ab);
	public static final Integer[] D3 = join(DIGIT, Ac);
	public static final Integer[] D4 = join(DIGIT, Ad);
	public static final Integer[] D5 = join(DIGIT, Ae);
	public static final Integer[] D6 = join(DIGIT, Af);
	public static final Integer[] D7 = join(DIGIT, Ag);
	public static final Integer[] D8 = join(DIGIT, Ah);
	public static final Integer[] D9 = join(DIGIT, Ai);
	public static final Integer[] D0 = join(DIGIT, Aj);
	public static final HashMap<Integer, KeyData> NUMBERS = new HashMap<Integer, KeyData>();
	static {
		NUMBERS.put(N0, KD_0);
		NUMBERS.put(N1, KD_1);
		NUMBERS.put(N2, KD_2);
		NUMBERS.put(N3, KD_3);
		NUMBERS.put(N4, KD_4);
		NUMBERS.put(N5, KD_5);
		NUMBERS.put(N6, KD_6);
		NUMBERS.put(N7, KD_7);
		NUMBERS.put(N8, KD_8);
		NUMBERS.put(N9, KD_9);
	}

	// MUSIC
	public static final Integer[] NATURAL = join(DIGIT, N1); // No Arial char
	public static final Integer[] FLAT = join(DIGIT, GROUP_OPEN); // No Arial char
	public static final Integer[] SHARP = join(DIGIT, N3);

	// SIMPLE PUNCTUATION
	public static final Integer APOSTROPHE = 4;
	public static final Integer COLON = 18;
	public static final Integer COMMA = 2;
	public static final Integer EXCLAMATION = 22;
	public static final Integer FULLSTOP = 50;
	public static final Integer HYPHEN = 36;
	public static final Integer PRIME = 54;
	public static final Integer QUESTION = 38;
	public static final Integer SEMICOLON = 6;

	// COMPLEX PUNCTUATION
	public static final Integer[] DOUBLE_PRIME = join(PRIME, PRIME);

	// GROUP PUNCTUATION
	public static final Integer[] ANGLE_BRACKET_OPEN = join(SHIFT8, GROUP_OPEN);
	public static final Integer[] ANGLE_BRACKET_CLOSE = join(SHIFT8, GROUP_CLOSE);
	public static final Integer[] CURLY_BRACKET_OPEN = join(SHIFT56, GROUP_OPEN);
	public static final Integer[] CURLY_BRACKET_CLOSE = join(SHIFT56, GROUP_CLOSE);
	public static final Integer[] ROUND_BRACKET_OPEN = join(SHIFT16, GROUP_OPEN);
	public static final Integer[] ROUND_BRACKET_CLOSE = join(SHIFT16, GROUP_CLOSE);
	public static final Integer[] SQUARE_BRACKET_OPEN = join(SHIFT40, GROUP_OPEN);
	public static final Integer[] SQUARE_BRACKET_CLOSE = join(SHIFT40, GROUP_CLOSE);

	// SHIFT 8 / CURRENCY
	public static final Integer[] AMPERSAND = join(SHIFT8, 47);
	public static final Integer[] AT_SIGN = join(SHIFT8, Aa);
	public static final Integer[] CARET = join(SHIFT8, 34);
	public static final Integer[] CENT = join(SHIFT8, Ac);
	public static final Integer[] DOLLAR = join(SHIFT8, As);
	public static final Integer[] EURO = join(SHIFT8, Ae);
	public static final Integer[] FRANC = join(SHIFT8, Af);
	public static final Integer[] GBP = join(SHIFT8, Al);
	public static final Integer[] GRREATER_THAN = ANGLE_BRACKET_CLOSE;
	public static final Integer[] LESS_THAN = ANGLE_BRACKET_OPEN;
	public static final Integer[] NAIRA = join(SHIFT8, An);
	public static final Integer[] TILDE = join(SHIFT8, 20);
	public static final Integer[] YEN = join(SHIFT8, Ay);
	// SHIFT 8 - COMBINING CHARACTERS
	public static final Integer[] BREVE = join(SHIFT8, 44);
	public static final Integer[] MACRON = join(SHIFT8, HYPHEN);
	public static final Integer[] SOLIDUS = join(SHIFT8, N1);
	public static final Integer[] STRIKETHROUGH = join(SHIFT8, COLON);
	// SHIFT 8 -> SHIFT 32
	public static final Integer[] DAGGER = join(SHIFT8_32, N4);
	public static final Integer[] DOUBLE_DAGGER = join(SHIFT8_32, N7);

	// SHIFT 16 / MATHS
	public static final Integer[] ASTERISK = join(SHIFT16, 20);
	public static final Integer[] DITTO = join(SHIFT16, 2);
	public static final Integer[] DIVIDE = join(SHIFT16, 12);
	public static final Integer[] EQUALS = join(SHIFT16, PRIME);
	public static final Integer[] MINUS = join(SHIFT16, HYPHEN);
	public static final Integer[] MULTIPLY = join(SHIFT16, QUESTION);
	public static final Integer[] PLUS = join(SHIFT16, EXCLAMATION);

	// SHIFT 24
	public static final Integer[] COPYRIGHT = join(SHIFT24, Ac);
	public static final Integer[] DEGREES = join(SHIFT24, Aj);
	public static final Integer[] PARAGRAPH = join(SHIFT24, Ap);
	public static final Integer[] REGISTERED = join(SHIFT24, Ar);
	public static final Integer[] SECTION = join(SHIFT24, As);
	public static final Integer[] TRADEMARK = join(SHIFT24, At);
	public static final Integer[] FEMALE = join(SHIFT24, Ax);
	public static final Integer[] MALE = join(SHIFT24, Ay);
	// SHIFT 24 - COMBINING CHARACTERS
	public static final Integer[] ACUTE = join(SHIFT24, 12);
	public static final Integer[] CARON = join(SHIFT24, 44);
	public static final Integer[] CEDILLA = join(SHIFT24, 47);
	public static final Integer[] CIRCUMFLEX = join(SHIFT24, N3);
	public static final Integer[] DIAERESIS = join(SHIFT24, COLON);
	public static final Integer[] GRAVE = join(SHIFT24, N1);
	public static final Integer[] RING = join(SHIFT24, N6);
	public static final Integer[] TILDE_COMB = join(SHIFT24, N7);

	// SHIFT 40
	public static final Integer[] PERCENT = join(SHIFT40, 52);
	public static final Integer[] UNDERSCORE = join(SHIFT40, HYPHEN);

	// SHIFT 56
	public static final Integer[] BACK_SLASH = join(SHIFT56, N1);
	public static final Integer[] BULLET = join(SHIFT56, FULLSTOP);
	public static final Integer[] FORWARD_SLASH = join(SHIFT56, 12);
	public static final Integer[] NUMBER = join(SHIFT56, N4);

	public static final HashMap<Integer[], KeyData[]> MODIFIERS = new HashMap<Integer[], KeyData[]>();
	static {
		MODIFIERS.put(ACUTE, join(KD_ACUTE));
		MODIFIERS.put(BREVE, join(KD_BREVE));
		MODIFIERS.put(CARON, join(KD_CARON));
		MODIFIERS.put(CEDILLA, join(KD_CEDILLA));
		MODIFIERS.put(CIRCUMFLEX, join(KD_CIRCUMFLEX));
		MODIFIERS.put(DIAERESIS, join(KD_DIAERESIS));
		MODIFIERS.put(GRAVE, join(KD_GRAVE));
		MODIFIERS.put(MACRON, join(KD_MACRON));
		MODIFIERS.put(RING, join(KD_RING));
		MODIFIERS.put(SOLIDUS, join(KD_SOLIDUS));
		MODIFIERS.put(STRIKETHROUGH, join(KD_STRIKETHROUGH));
		MODIFIERS.put(TILDE_COMB, join(KD_TILDE_COMB));
	}

	// GREEK ALPHABET
	private static final Integer[] Galpha = join(SHIFT40, Aa);
	private static final Integer[] Gbeta = join(SHIFT40, Ab);
	private static final Integer[] Ggamma = join(SHIFT40, Ag);
	private static final Integer[] Gdelta = join(SHIFT40, Ad);
	private static final Integer[] Gepsilon = join(SHIFT40, Ae);
	private static final Integer[] Gzeta = join(SHIFT40, Az);
	private static final Integer[] Geta = join(SHIFT40, N5);
	private static final Integer[] Gtheta = join(SHIFT40, N4);
	private static final Integer[] Giota = join(SHIFT40, Ai);
	private static final Integer[] Gkappa = join(SHIFT40, Ak);
	private static final Integer[] Glambda = join(SHIFT40, Al);
	private static final Integer[] Gmu = join(SHIFT40, Am);
	private static final Integer[] Gnu = join(SHIFT40, An);
	private static final Integer[] Gxi = join(SHIFT40, Ax);
	private static final Integer[] Gomicron = join(SHIFT40, Ao);
	private static final Integer[] Gpi = join(SHIFT40, Ap);
	private static final Integer[] Grho = join(SHIFT40, Ar);
	private static final Integer[] Gsigma = join(SHIFT40, As);
	private static final Integer[] Gtau = join(SHIFT40, At);
	private static final Integer[] Gupsilon = join(SHIFT40, Au);
	private static final Integer[] Gphi = join(SHIFT40, Af);
	private static final Integer[] Gchi = join(SHIFT40, 47);
	private static final Integer[] Gpsi = join(SHIFT40, Ay);
	private static final Integer[] Gomega = join(SHIFT40, Aw);
	public static final HashMap<Integer[], KeyData[]> GREEK = new HashMap<Integer[], KeyData[]>();
	static {
		GREEK.put(Galpha, new KeyData[] {KD_Galpha, KD_GALPHA});
		GREEK.put(Gbeta, new KeyData[] {KD_Gbeta, KD_GBETA});
		GREEK.put(Ggamma, new KeyData[] {KD_Ggamma, KD_GGAMMA});
		GREEK.put(Gdelta, new KeyData[] {KD_Gdelta, KD_GDELTA});
		GREEK.put(Gepsilon, new KeyData[] {KD_Gepsilon, KD_GEPSILON});
		GREEK.put(Gzeta, new KeyData[] {KD_Gzeta, KD_GZETA});
		GREEK.put(Geta, new KeyData[] {KD_Geta, KD_GETA});
		GREEK.put(Gtheta, new KeyData[] {KD_Gtheta, KD_GTHETA});
		GREEK.put(Giota, new KeyData[] {KD_Giota, KD_GIOTA});
		GREEK.put(Gkappa, new KeyData[] {KD_Gkappa, KD_GKAPPA});
		GREEK.put(Glambda, new KeyData[] {KD_Glambda, KD_GLAMBDA});
		GREEK.put(Gmu, new KeyData[] {KD_Gmu, KD_GMU});
		GREEK.put(Gnu, new KeyData[] {KD_Gnu, KD_GNU});
		GREEK.put(Gxi, new KeyData[] {KD_Gxi, KD_GXI});
		GREEK.put(Gomicron, new KeyData[] {KD_Gomicron, KD_GOMICRON});
		GREEK.put(Gpi, new KeyData[] {KD_Gpi, KD_GPI});
		GREEK.put(Grho, new KeyData[] {KD_Grho, KD_GRHO});
		GREEK.put(Gsigma, new KeyData[] {KD_Gsigma, KD_GSIGMA});
		GREEK.put(Gtau, new KeyData[] {KD_Gtau, KD_GTAU});
		GREEK.put(Gupsilon, new KeyData[] {KD_Gupsilon, KD_GUPSILON});
		GREEK.put(Gphi, new KeyData[] {KD_Gphi, KD_GPHI});
		GREEK.put(Gchi, new KeyData[] {KD_Gchi, KD_GCHI});
		GREEK.put(Gpsi, new KeyData[] {KD_Gpsi, KD_GPSI});
		GREEK.put(Gomega, new KeyData[] {KD_Gomega, KD_GOMEGA});
	}
}