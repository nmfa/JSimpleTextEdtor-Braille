package com.anas.jsimpletexteditor;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayDeque;
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

	public ArrayList<ArrayList<KeyData>> keyData;
	public HashMap<Integer, MapData> map;
	public int type;
    Logger log = Logger.getLogger("MapData");

	MapData() {
		this.keyData = null;
		this.map = new HashMap<Integer, MapData>();
		this.type = OVERFLOWS;
	}

	MapData(int tp, KeyData[] kdLower, KeyData[] kdUpper) {
		this(tp, new ArrayList<KeyData>(Arrays.asList(kdLower)), new ArrayList<KeyData>(Arrays.asList(kdUpper)));
	}

	// Can't use List.of as arguments could be null.
	private static ArrayList<ArrayList<KeyData>> listOf(ArrayList<KeyData> kdLower, ArrayList<KeyData> kdUpper) {
		ArrayList<ArrayList<KeyData>> result = new ArrayList<ArrayList<KeyData>>();
		result.add(kdLower);
		result.add(kdUpper);
		return result;
	}
	MapData(int tp, ArrayList<KeyData> kdLower, ArrayList<KeyData> kdUpper) {
		this(tp, listOf(kdLower, kdUpper));
	}

	MapData(int tp, ArrayList<ArrayList<KeyData>> kd) {
		this.keyData = kd;
		this.map = null;
		this.type = tp;
		if ((type & OVERFLOWS) > 0) {
			this.map = new HashMap<Integer, MapData>();
		}
		if (type == OVERFLOWS) /*ONLY*/ {
			log.severe("MAPDATA CONTAINING KEYDATA SHOULD HAVE A CONCRETE TYPE");
		}
	}

	public void addMap() {
		if (map == null) map = new HashMap<Integer, MapData>();
		type = type | OVERFLOWS;
	}

	public ArrayList<KeyData> getKeyData() {
		return getKeyData(false);
	}

	public ArrayList<KeyData> getKeyData(boolean shift) {
		if ((isAlphabet() && !isCharacter()) || isString()) {
			return keyData.get((shift) ? 1 : 0);
		} else if (isCharacter() || isWhitespace()) {
			return keyData.get(0);
		} else {
			return null;
		}
	}

	public boolean overflows() {return (type & OVERFLOWS) > 0;}
	public boolean isConcrete() {return (type & CONCRETE) > 0;}
	public boolean isFinal() {return (type & FINAL) > 0;}
	public boolean isModifier() {return (type & MODIFIER) > 0;}
	public boolean isLigature() {return (type & LIGATURE) > 0 && (type & ~(LIGATURE | OVERFLOWS)) == 0;}
	public boolean hasLigature() {return ((type & ALPHABET) > 0 && (type & LIGATURE) > 0);}
	public boolean isAlphabet() {return (type & ALPHABET) > 0;}
	public boolean isString() {return (type & STRING) > 0;}
	public boolean isCharacter() {return (type & CHARACTER) > 0;}
	public boolean isOverflow() {return (type & OVERFLOWS) > 0;}
	public boolean isWhitespace() {return (type & WHITESPACE) > 0;}
	public boolean isStandAloneMarker() {return (type & STANDALONE) > 0;}
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


// Need the KeyData pointer as MapData alone doesn't tell us which case was used.
class History {
	ArrayList<Integer> pinCodeList;
	MapData mapData;
	ArrayList<KeyData> keyData;

	History(ArrayList<Integer> pcl, MapData md, ArrayList<KeyData> kd) {
		this.pinCodeList = new ArrayList<Integer>(pcl);
		this.mapData = md;
		this.keyData = kd;
	}
}


public class TextAreaBraille extends JTextArea {
    private static Logger log = Logger.getLogger("TextAreaBraille");

	private static enum LOCK {
		OFF, CHAR, WORD, FULL {
			@Override
			public LOCK next() {
				return values()[0];
			};
		};
		public LOCK next() {return values()[ordinal() + 1];}
		public LOCK charReset()  {return (ordinal() < 2) ? OFF : values()[ordinal()];}
		public LOCK wordReset() {return (ordinal() < 3) ? OFF : FULL;}
		public LOCK reset() {return OFF;}
		public boolean isOn() {return ordinal() > 0;}
	};

    private int currentPinCode = 0;
	private ArrayList<Integer> currentPinCodesList = new ArrayList<Integer>();
    private boolean lastKeyDown = false;
	private ArrayDeque<History> recentHistory = new ArrayDeque<History>(2);
	private int wordLength = 0;
	private LOCK grade1 = LOCK.OFF;
	private LOCK shift = LOCK.OFF;
	private LOCK shift40 = LOCK.OFF;
	private LOCK digit = LOCK.OFF;

    TextAreaBraille() {
        super();
		// Initialise as if we've just had whitespace, arbitrarily ENTER.
		recentHistory.add(new History(new ArrayList<Integer>(Arrays.asList(ENTER)), BRAILLE_MAP.get(ENTER), BRAILLE_MAP.get(ENTER).getKeyData()));
		recentHistory.add(new History(new ArrayList<Integer>(Arrays.asList(ENTER)), BRAILLE_MAP.get(ENTER), BRAILLE_MAP.get(ENTER).getKeyData()));
    }


    @Override
    protected void processKeyEvent(KeyEvent e) {
		// Not setting lastKeyDown here, as it'll make the onKeyDown and onKeyUp logic consistent for when it is
		// moved to an API which has those functions, rather than this single API function.
		if (e.getID() == KeyEvent.KEY_PRESSED) {
			onKeyDown(e);
        } else if (e.getID() == KeyEvent.KEY_RELEASED) {
			log.info("PROCESSING EVENT: " + currentPinCode + ", " + currentPinCodesList.toString() + ", " + KEY_PIN_MAP.getOrDefault(e.getKeyCode(), 0));
			onKeyUp(e);
			log.info("PROCESSED: " + currentPinCode + ", " + currentPinCodesList.toString() + ", " + shift);
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
		Integer pin = KEY_PIN_MAP.getOrDefault(keyCode, 0);
		currentPinCode = currentPinCode | pin;
		lastKeyDown = true;
	}


	private void onKeyUp(KeyEvent e) {
		int keyCode = e.getKeyCode();
		Integer pin = KEY_PIN_MAP.getOrDefault(keyCode, 0);

		// Pass through. Reset shift below Caps Lock (3) for white space.
		switch (keyCode) {
			case KeyEvent.VK_ENTER:
				currentPinCode = ENTER;
				break;

			case KeyEvent.VK_SPACE:
				currentPinCode = SPACE;
				break;

			case KeyEvent.VK_BACK_SPACE:
				sendKeyEvents(e.getComponent(), e. getWhen(), BRAILLE_MAP.get(-keyCode).getKeyData());
				updateRecentHistory(BRAILLE_MAP.get(-keyCode), BRAILLE_MAP.get(-keyCode).getKeyData());
				wordResets();
				currentPinCodesList.clear();
				qualifyPinCode();
				lastKeyDown = false;
				return;
		}

		// Getting engaged when it's the last key left of a larger combination, so only after a keyDown
		// Can't have shift and digit engaged simultaneously, as they both use A-J.
		if (lastKeyDown && 
			(currentPinCode == GRADE1 ||
			 currentPinCode == SHIFT ||
			 currentPinCode == SHIFT40 || 
			 currentPinCode == DIGIT)) {
			if (!(currentPinCodesList.size() > 0 && currentPinCodesList.get(0) == SHIFT8)) {
				if (currentPinCode == GRADE1) {
					grade1 = grade1.next();
					log.info("GRADE1: " + grade1);
					// Doesn't reset shifts or digit as only affects contraction.
				} else if (currentPinCode == SHIFT) {
					shift = shift.next();
					// Doesn't reset other shifts
					digit = digit.reset();
					currentPinCode = 0;
					log.info("SHIFT: " + shift);
				} else if (currentPinCode == SHIFT40) {
					shift40 = shift40.next();
					// Doesn't reset other shifts
					digit = digit.reset();
					currentPinCode = ~(~currentPinCode | pin);
					log.info("SHIFT40: " + shift40);
					popQualifiers();
					qualifyPinCode();
					} else if (currentPinCode == DIGIT) {
					digit = digit.next();
					// Resets both shifts.
					shift = shift.reset();
					shift40 = shift40.reset();
					currentPinCode = ~(~currentPinCode | pin);
					log.info("DIGIT: " + digit);
					popQualifiers();
					qualifyPinCode();
				}

				lastKeyDown = false;
				return;
			}
		}
		log.info("HERE: " + currentPinCode + ", " + currentPinCodesList.toString() + ", " + shift);

        // If keyUp then find the assocaited MapData, if it exists.
        if (lastKeyDown) {
			if (isWhitespace(currentPinCode)) popQualifiers();
			currentPinCodesList.add(currentPinCode);
			log.info("PIN CODE SEQUENCE: " + currentPinCodesList.toString());
			MapData md = parsePinCodes();
			if (md == null) {
				int originalPinCodesListLength = currentPinCodesList.size();
				// This should only ever recurse once.
				currentPinCodesList.clear();
				//pinCodeOverflow = 0;
				charResets();
				qualifyPinCode();
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
				if (md.isAlphabet() && !md.isCharacter()) {
					ArrayList<KeyData> kd = md.getKeyData(shift.isOn());
					sendKeyEvents(e.getComponent(), e.getWhen(), kd);
					updateRecentHistory(md, kd);
					wordLength++;
				} else if (md.isFinal()) {
					ArrayList<KeyData> kd = md.getKeyData();
					sendKeyEvents(e.getComponent(), e.getWhen(), kd);
					// This will not be quite accurate for many modified letters,
					// but they can't be part of contractions, and that is the purpose
					// of wordLength.
					wordLength += md.keyData.size();
					updateRecentHistory(md, kd);
				}
			}
			//if (pinCodeOverflow == 0) {
			if (!md.isOverflow()) {
				// Reset the pin code sequence, as one way or another it is done with.
				currentPinCodesList.clear();
			}

			// Deal with word locks of shift and digit
			if (md.isWhitespace()) {
				wordResets();
				wordLength = 0;
			}

			//if (pinCodeOverflow == 0) {
			if (!md.isOverflow()) {
				charResets();
				// If shift or digit are locked, add to the pin code sequence now.
				// They won't both be set. Shifts can be set together.
				qualifyPinCode();
			}
		}

		if (currentPinCode == SPACE) {
			currentPinCode = 0;
		} else {
			currentPinCode = ~(~currentPinCode | pin);
		}
		lastKeyDown = false;
	}

	private void charResets() {
		grade1 = grade1.charReset();
		shift = shift.charReset();
		shift40 = shift40.charReset();
		digit = digit.charReset();
	}

	private void wordResets() {
		grade1 = grade1.wordReset();
		shift = shift.wordReset();
		shift40 = shift40.wordReset();
		digit = digit.wordReset();
	}

	private void qualifyPinCode() {
		// GRADE1 and SHIFT are operating just off the class variable.
		// SHIFT40 and DIGIT are used as part of the mappings currently,
		// so for ease need to be part of the pin code array.
		if (shift40.isOn()) currentPinCodesList.add(SHIFT40);
		if (digit.isOn()) currentPinCodesList.add(DIGIT);
	}

	private void updateRecentHistory(MapData md, ArrayList<KeyData> kd) {
		recentHistory.removeFirst();
		recentHistory.addLast(new History(currentPinCodesList, md, kd));
	}

	private void popQualifiers() {
		if (currentPinCodesList.size() == 0) return;
		int finalIndex = currentPinCodesList.size() - 1;
		int finalPinCode = currentPinCodesList.get(finalIndex);
		if (((finalPinCode & SHIFT40) > 0 && (finalPinCode & ~SHIFT40) == 0) || finalPinCode == DIGIT) {
			currentPinCodesList.remove(finalIndex);
			// For shift combinations.
			if (finalPinCode != DIGIT) popQualifiers();
		}
	}

	private boolean isWhitespace(int pinCode) {
		for (int code: WHITESPACES) {
			if (pinCode == code) return true;
		}
		return false;
	}


	private MapData __getMapDataFromBrailleMap(int[] pcIndex) {
		return __getMapDataFromMap(pcIndex, BRAILLE_MAP);
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
		MapData mdModifier = null;
		MapData mdLigature = null;

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
						qualifyPinCode();
						currentPinCodesList.add(finalPinCode);
						return parsePinCodes();
					}
				}
				if (md.isAlphabet()) {
					MapData result = md;
					// Apply any modifier
					if (mdModifier != null) {
						MapData mdModified =
							getModifiedAlphabet(mdModifier, currentPinCode);
						if (mdModified == null) {
							result = new MapData(MapData.ALPHABET | MapData.CHARACTER, join(md.getKeyData(shift.isOn()), mdModifier.getKeyData()), null);
						} else {
							// On rare combinations there is only a normalized char for one case.
							if (mdModified.getKeyData(shift.isOn()) == null) {
								result = new MapData(MapData.ALPHABET | MapData.CHARACTER, join(md.getKeyData(shift.isOn()), mdModifier.getKeyData()), null); 
							} else {
								result = mdModified;
							}
						}
					}
					if (mdLigature != null && md.isAlphabet()) {
						// Is the precidimg character an alphabet?
						History lastHistory = recentHistory.getLast();
						if (lastHistory.mapData.isAlphabet()) {
							// Is there a single character for this ligature?
							Integer lastAlphabetPinCode =
								lastHistory.pinCodeList.get(lastHistory.pinCodeList.size() - 1);
							MapData leftMapData = BRAILLE_MAP.get(lastAlphabetPinCode);	
							MapData rightMapData = null;
							log.info("LEFT MAP DATA TYPE: " + leftMapData.type);
							if (leftMapData.hasLigature()) {
								rightMapData = leftMapData.map.get(currentPinCode);
							}
							if (rightMapData != null && rightMapData.getKeyData(shift.isOn()) != null) {
								result = new MapData(MapData.ALPHABET | MapData.CHARACTER, join(KD_BACKSPACE, rightMapData.getKeyData(shift.isOn())), null);
							} else {
								ArrayList<KeyData> lkd = new ArrayList<KeyData>();
								lkd.add(KD_LIGATURE[LEFT]);
								if (md.isAlphabet()) {
									lkd.addAll(md.getKeyData(shift.isOn()));
								} else {
									lkd.addAll(md.getKeyData());
								}
								lkd.add(KD_LIGATURE[RIGHT]);
								result = new MapData(MapData.ALPHABET | MapData.CHARACTER, lkd, null);
							}
						}
					}
					return result;
				} else { // WHITESPACE, CHARACTER, STRING, none of which can't be modified.
					return md;
				}
			}
			if (md.isModifier()) mdModifier = md;
			if (md.isLigature()) {mdLigature = md;}
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


	private static void addAlphabetsToBrailleMap() {
		for (Integer code: ALPHABET.keySet()) {
			Integer[] codes = {code};
			addToBrailleMap(codes, MapData.ALPHABET, ALPHABET.get(code)[LOWER], ALPHABET.get(code)[UPPER]);
		}
		for (Integer[] codes: GREEK.keySet()) {
			addToBrailleMap(codes, MapData.ALPHABET, GREEK.get(codes)[LOWER], GREEK.get(codes)[UPPER]);
		}
	}

	private static void addCombiningCharsToBrailleMap() {
		for (Integer[] mCodes: MODIFIERS.keySet()) {
			KeyData[] mKeyData = MODIFIERS.get(mCodes);
			addToBrailleMap(mCodes, MapData.MODIFIER | MapData.OVERFLOWS, mKeyData, null);
			for (int aCode: ALPHABET.keySet()) {
				// THe expectation is that these will be pairs of lower and upper case modified characters.
				boolean modifiedPair = false;
				KeyData[] pair = new KeyData[2];
				KeyData[][] aKeyData = ALPHABET.get(aCode);
				String combination = String.valueOf(aKeyData[LOWER][0].keyChar) + String.valueOf(mKeyData[0].keyChar);
				char keyChar = Normalizer.normalize(combination, Form.NFC).charAt(0);
				if (keyChar != aKeyData[LOWER][0].keyChar) {
					pair[LOWER] = new KeyData(keyChar);
					modifiedPair = true;
				}
				combination = String.valueOf(aKeyData[UPPER][0].keyChar) + String.valueOf(mKeyData[0].keyChar);
				keyChar = Normalizer.normalize(combination, Form.NFC).charAt(0);
				if (keyChar != aKeyData[UPPER][0].keyChar) {
					pair[UPPER] = new KeyData(keyChar);
					if (!modifiedPair) {
						pair[LOWER] = null;
						log.warning("UPPER MODIFIED CHARACTER, BUT NO LOWER: " + aKeyData[LOWER][0].keyChar + String.format("\\u%04x", (int) mKeyData[0].keyChar));
					}
					modifiedPair = true;
				} else if (modifiedPair) {
					pair[UPPER] = null;
					log.warning("LOWER MODIFIED CHARACTER, BUT NO UPPER: " + aKeyData[UPPER][0].keyChar + String.format("\\u%04x", (int) mKeyData[0].keyChar));
				}
				if (modifiedPair) {
					addToBrailleMap(join(mCodes, aCode), MapData.ALPHABET | MapData.MODIFIER, pair[LOWER], pair[UPPER]);
				}
			}
		}
	}

	private static void addCharToBrailleMap(Integer[] pinCodes, KeyData... keyData) {
		addToBrailleMap(pinCodes, MapData.CHARACTER, keyData, null);
	}
	private static void addCharToBrailleMap(Integer pinCode, KeyData... keyData) {
		Integer[] pinCodes = {pinCode};
		addCharToBrailleMap(pinCodes, keyData);
	}

	private static void addStandAloneToBrailleMap(Integer[] pinCodes, KeyData... keyData) {
		addToBrailleMap(pinCodes, MapData.CHARACTER | MapData.STANDALONE, keyData, null);
	}
	private static void addStandAloneToBrailleMap(Integer pinCode, KeyData... keyData) {
		Integer[] pinCodes = {pinCode};
		addStandAloneToBrailleMap(pinCodes, keyData);
	}

	private static void addWhitespaceToBrailleMap(Integer pinCode, KeyData... keyData) {
		Integer[] pinCodes = {pinCode};
		addToBrailleMap(pinCodes, MapData.WHITESPACE | MapData.STANDALONE, keyData, null);
	}

	private static void addLigaturesToBrailleMap() {
		for (Integer[] ligature: LIGATURES.keySet()) {
			addToBrailleMap(ligature, MapData.ALPHABET, LIGATURES.get(ligature)[LOWER], LIGATURES.get(ligature)[UPPER]);
			// Specifically disable OVERFLOWS for the left alphabet character.
			BRAILLE_MAP.get(ligature[LEFT]).type = (BRAILLE_MAP.get(ligature[LEFT]).type | MapData.LIGATURE) & ~MapData.OVERFLOWS;
		}
	}

	private static void addToBrailleMap(Integer[] pinCodes, int type, KeyData keyDataLower, KeyData keyDataUpper) {
		addToBrailleMap(pinCodes,
						type,
						new KeyData[] {keyDataLower},
						new KeyData[] {keyDataUpper});
	}

	private static void addToBrailleMap(Integer[] pinCodes, int type, KeyData[] keyDataLower, KeyData[] keyDataUpper) {
		addToBrailleMap(pinCodes,
						type,
						0,
						new ArrayList<KeyData>(Arrays.asList(keyDataLower)),
						(keyDataUpper == null) ? null : new ArrayList<KeyData>(Arrays.asList(keyDataUpper)),
						BRAILLE_MAP);
	}

	private static void addToBrailleMap(Integer[] pinCodes,
										int type,
										int pcIndex,
										ArrayList<KeyData> keyDataLower,
										ArrayList<KeyData> keyDataUpper,
										HashMap<Integer, MapData> map) {
		MapData mapData =  map.get(pinCodes[pcIndex]);
		ArrayList<ArrayList<KeyData>> keyData = new ArrayList<ArrayList<KeyData>>();
		keyData.add(keyDataLower);
		if (keyDataUpper != null) {
			keyData.add(keyDataUpper);
		}
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
				addToBrailleMap(pinCodes, type, pcIndex + 1, keyDataLower, keyDataUpper, mapData.map);
			}
		} else {
			if (pcIndex + 1 == pinCodes.length) {
				MapData md = new MapData(type, keyData);
				map.put(pinCodes[pcIndex], md);
			} else {
				MapData md = new MapData();
				map.put(pinCodes[pcIndex], md);
				addToBrailleMap(pinCodes, type, pcIndex + 1, keyDataLower, keyDataUpper, md.map);
			}
		}
	}


	@SafeVarargs
	private static <T> T[] join(T... items) {
		return Arrays.copyOf(items, items.length);
	}
	@SafeVarargs
	private static <T> T[] join(T[] arr, T... items) {
		T[] newArr = Arrays.copyOf(arr, arr.length + items.length);
		System.arraycopy(items, 0, newArr, arr.length, items.length);
		return newArr;
	}
	@SafeVarargs
	private static <T> ArrayList<T> join(ArrayList<T>... items) {
		ArrayList<T> result = new ArrayList<T>();
		for (ArrayList<T> item: items) {
			result.addAll(item);
		}
		return result;
	}
	private static <T> ArrayList<T> join (T item, ArrayList<T> itemAL) {
		ArrayList<T> result = new ArrayList<T>();
		result.add(item);
		result.addAll(itemAL);
		return result;
	}

	private static KeyData[][] link(KeyData kdLower, KeyData kdUpper) {
		return link(new KeyData[] {kdLower}, new KeyData[] {kdUpper});
	}

	private static KeyData[][] link(KeyData[] kdLower, KeyData[] kdUpper) {
		KeyData[][] result = {kdLower, kdUpper};
		return result;
	}


    private static void populateBrailleMap() {
		// ENGLISH ALPHABET
		addAlphabetsToBrailleMap();
		addToBrailleMap(ENG, MapData.ALPHABET, new KeyData('ŋ', 331), new KeyData('Ŋ', 330));
		addToBrailleMap(SCHWA, MapData.ALPHABET, new KeyData('ə', 601), new KeyData('Ə', 399));
		addCombiningCharsToBrailleMap();
		addToBrailleMap(LIGATURE, MapData.LIGATURE | MapData.OVERFLOWS, KD_LIGATURE, null);
		addLigaturesToBrailleMap();

		addWhitespaceToBrailleMap(ENTER, KD_ENTER);
		addWhitespaceToBrailleMap(SPACE, KD_SPACE);
 	
		// NUMBERS
		// COMPUTER NOTATION
		addToBrailleMap(join(DIGIT, SHIFT16), MapData.CHARACTER | MapData.OVERFLOWS | MapData.STANDALONE, KD_SPACE, null);
		for (int i = 0; i < 10; i++) {
			addCharToBrailleMap(N_TEN[i], KD_TEN[i]);
			addCharToBrailleMap(join(DIGIT, D_TEN[i]), KD_TEN[i]);
			addCharToBrailleMap(join(DIGIT, SHIFT16, D_TEN[i]),  KD_TEN[i]);
		}
		Integer[][] prefixes = {{DIGIT}, {DIGIT, SHIFT16}};
		for (Integer[] prefix: prefixes) {
			addStandAloneToBrailleMap(join(prefix, COMMA), KD_COMMA);
			addStandAloneToBrailleMap(join(prefix, FULLSTOP), KD_FULLSTOP);
			addCharToBrailleMap(join(prefix, EXCLAMATION), KD_PLUS);
			addStandAloneToBrailleMap(join(prefix, HYPHEN), KD_MINUS);
			addCharToBrailleMap(join(prefix, 20), KD_ASTERISK);
			addStandAloneToBrailleMap(join(prefix, 12), KD_FORWARD_SLASH);
			addStandAloneToBrailleMap(join(prefix, N1), KD_BACK_SLASH);
			addCharToBrailleMap(join(prefix, PRIME), KD_EQUALS);
			addStandAloneToBrailleMap(join(prefix, GROUP_OPEN), KD_LESS_THAN);
			addStandAloneToBrailleMap(join(prefix, GROUP_CLOSE), KD_GREATER_THAN);
			addStandAloneToBrailleMap(join(prefix, COLON), KD_COLON);
		}

		// MUSIC
        addCharToBrailleMap(SHARP, new KeyData('♯'));

		// SIMPLE PUNCTUATION
		addStandAloneToBrailleMap(ANGLE_QUOTE_OPEN, new KeyData('«'));
		addStandAloneToBrailleMap(ANGLE_QUOTE_CLOSE, new KeyData('»'));
		addStandAloneToBrailleMap(APOSTROPHE, new KeyData('\''));
		addStandAloneToBrailleMap(BACK_SLASH, KD_BACK_SLASH);
		addStandAloneToBrailleMap(BULLET, new KeyData('•'));
		addStandAloneToBrailleMap(CARET, new KeyData('^', true));
		addStandAloneToBrailleMap(COLON, KD_COLON);
		addStandAloneToBrailleMap(COMMA, KD_COMMA);
		addStandAloneToBrailleMap(EXCLAMATION, new KeyData('!', KeyEvent.VK_EXCLAMATION_MARK, true));
		addStandAloneToBrailleMap(FORWARD_SLASH, KD_FORWARD_SLASH);
		addStandAloneToBrailleMap(FULLSTOP, KD_FULLSTOP);
		addStandAloneToBrailleMap(HYPHEN, KD_MINUS);
		addStandAloneToBrailleMap(NUMBER, new KeyData('#'));
		addToBrailleMap(join(PRIME), MapData.ALPHABET | MapData.OVERFLOWS | MapData.STANDALONE, new KeyData('′'), KD_QUOTE);
		addStandAloneToBrailleMap(QUOTE_OPEN, new KeyData('“'));
		addStandAloneToBrailleMap(QUOTE_CLOSE, new KeyData('”'));
		addStandAloneToBrailleMap(SEMICOLON, new KeyData(';', KeyEvent.VK_SEMICOLON));
		addStandAloneToBrailleMap(TILDE, new KeyData('~', true));
		addCharToBrailleMap(UNDERSCORE, new KeyData('_', KeyEvent.VK_UNDERSCORE, true));

		// COMPLEX PUNCTUATION
		addToBrailleMap(join(QUESTION), MapData.ALPHABET | MapData.STANDALONE, new KeyData('?'), new KeyData('‘'));
		addToBrailleMap(join(QUESTION_INVERTED), MapData.ALPHABET | MapData.STANDALONE, new KeyData('¿'), new KeyData('’'));
		addToBrailleMap(DOUBLE_PRIME, MapData.ALPHABET | MapData.STANDALONE, join(KD_BACKSPACE, new KeyData('″')), join(KD_QUOTE));

		// GROUP PUNCTUATION
		addStandAloneToBrailleMap(ANGLE_BRACKET_OPEN, KD_LESS_THAN);
		addStandAloneToBrailleMap(ANGLE_BRACKET_CLOSE, KD_GREATER_THAN);
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
		addStandAloneToBrailleMap(ASTERISK, KD_ASTERISK);
		addStandAloneToBrailleMap(DITTO, new KeyData('"', true));
		addStandAloneToBrailleMap(DIVIDE, new KeyData('÷'));
		addStandAloneToBrailleMap(EQUALS, KD_EQUALS);
		addStandAloneToBrailleMap(MINUS, KD_MINUS);
		addStandAloneToBrailleMap(MULTIPLY, new KeyData('×'));
		addStandAloneToBrailleMap(PERCENT, new KeyData('\u0025', 37));
		addStandAloneToBrailleMap(PLUS, KD_PLUS);
	
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
    	addCharToBrailleMap(BACKSPACE, KD_BACKSPACE);
	}

	private static final HashMap<Integer, Integer> KEY_PIN_MAP = new HashMap<Integer, Integer>();
	static {
		// MAP REAL KEYBOARD TO PINS
        KEY_PIN_MAP.put(70, 1);   // F
        KEY_PIN_MAP.put(68, 2);   // D
        KEY_PIN_MAP.put(83, 4);   // S
        KEY_PIN_MAP.put(74, 8);   // J
        KEY_PIN_MAP.put(75, 16);  // K
        KEY_PIN_MAP.put(76, 32);  // L
        KEY_PIN_MAP.put(65, 64);  // A
        KEY_PIN_MAP.put(59, 128); // ;
    }

	private static final Integer BACKSPACE = -KeyEvent.VK_BACK_SPACE;

	// SOME COMMON KEYDATA
	private static final KeyData KD_BACKSPACE = new KeyData('\b', KeyEvent.VK_BACK_SPACE);
	private static final KeyData KD_ENTER = new KeyData('\n', KeyEvent.VK_ENTER);
	private static final KeyData KD_SPACE = new KeyData(' ', KeyEvent.VK_SPACE);

	// STANDARD ALPHABET KEYDATA
	private static final KeyData KD_Aa = new KeyData('a');
	private static final KeyData KD_Ab = new KeyData('b');
	private static final KeyData KD_Ac = new KeyData('c');
	private static final KeyData KD_Ad = new KeyData('d');
	private static final KeyData KD_Ae = new KeyData('e');
	private static final KeyData KD_Af = new KeyData('f');
	private static final KeyData KD_Ag = new KeyData('g');
	private static final KeyData KD_Ah = new KeyData('h');
	private static final KeyData KD_Ai = new KeyData('i');
	private static final KeyData KD_Aj = new KeyData('j');
	private static final KeyData KD_Ak = new KeyData('k');
	private static final KeyData KD_Al = new KeyData('l');
	private static final KeyData KD_Am = new KeyData('m');
	private static final KeyData KD_An = new KeyData('n');
	private static final KeyData KD_Ao = new KeyData('o');
	private static final KeyData KD_Ap = new KeyData('p');
	private static final KeyData KD_Aq = new KeyData('q');
	private static final KeyData KD_Ar = new KeyData('r');
	private static final KeyData KD_As = new KeyData('s');
	private static final KeyData KD_At = new KeyData('t');
	private static final KeyData KD_Au = new KeyData('u');
	private static final KeyData KD_Av = new KeyData('v');
	private static final KeyData KD_Aw = new KeyData('w');
	private static final KeyData KD_Ax = new KeyData('x');
	private static final KeyData KD_Ay = new KeyData('y');
	private static final KeyData KD_Az = new KeyData('z');
	private static final KeyData KD_AA = new KeyData('A', true);
	private static final KeyData KD_AB = new KeyData('B', true);
	private static final KeyData KD_AC = new KeyData('C', true);
	private static final KeyData KD_AD = new KeyData('D', true);
	private static final KeyData KD_AE = new KeyData('E', true);
	private static final KeyData KD_AF = new KeyData('F', true);
	private static final KeyData KD_AG = new KeyData('G', true);
	private static final KeyData KD_AH = new KeyData('H', true);
	private static final KeyData KD_AI = new KeyData('I', true);
	private static final KeyData KD_AJ = new KeyData('J', true);
	private static final KeyData KD_AK = new KeyData('K', true);
	private static final KeyData KD_AL = new KeyData('L', true);
	private static final KeyData KD_AM = new KeyData('M', true);
	private static final KeyData KD_AN = new KeyData('N', true);
	private static final KeyData KD_AO = new KeyData('O', true);
	private static final KeyData KD_AP = new KeyData('P', true);
	private static final KeyData KD_AQ = new KeyData('Q', true);
	private static final KeyData KD_AR = new KeyData('R', true);
	private static final KeyData KD_AS = new KeyData('S', true);
	private static final KeyData KD_AT = new KeyData('T', true);
	private static final KeyData KD_AU = new KeyData('U', true);
	private static final KeyData KD_AV = new KeyData('V', true);
	private static final KeyData KD_AW = new KeyData('W', true);
	private static final KeyData KD_AX = new KeyData('X', true);
	private static final KeyData KD_AY = new KeyData('Y', true);
	private static final KeyData KD_AZ = new KeyData('Z', true);

	// LIGATURES
	private static final KeyData KD_Lae = new KeyData('æ');
	private static final KeyData KD_LAE = new KeyData('Æ');
	private static final KeyData KD_Lff = new KeyData('ﬀ');
	private static final KeyData KD_Lffi = new KeyData('ﬃ');
	private static final KeyData KD_Lffl = new KeyData('ﬄ');
	private static final KeyData KD_Lfi = new KeyData('ﬁ');
	private static final KeyData KD_Lfl = new KeyData('ﬂ');
	private static final KeyData KD_Lft = new KeyData('ﬅ');
	private static final KeyData KD_Lij = new KeyData('ĳ');
	private static final KeyData KD_LIJ = new KeyData('Ĳ');
	private static final KeyData KD_Loe = new KeyData('œ');
	private static final KeyData KD_LOE = new KeyData('Œ');
	private static final KeyData KD_Lth = new KeyData('þ');
	private static final KeyData KD_LTH = new KeyData('Þ');
	private static final KeyData KD_Lst = new KeyData('ﬆ');
	private static final KeyData KD_Lue = new KeyData('ᵫ');

	// HREEK ALPHABET
	private static final KeyData KD_Galpha = new KeyData('α');
	private static final KeyData KD_Gbeta = new KeyData('β');
	private static final KeyData KD_Ggamma = new KeyData('γ');
	private static final KeyData KD_Gdelta = new KeyData('δ');
	private static final KeyData KD_Gepsilon = new KeyData('ε');
	private static final KeyData KD_Gzeta = new KeyData('ζ');
	private static final KeyData KD_Geta = new KeyData('η');
	private static final KeyData KD_Gtheta = new KeyData('θ');
	private static final KeyData KD_Giota = new KeyData('ι');
	private static final KeyData KD_Gkappa = new KeyData('κ');
	private static final KeyData KD_Glambda = new KeyData('λ');
	private static final KeyData KD_Gmu = new KeyData('μ');
	private static final KeyData KD_Gnu = new KeyData('ν');
	private static final KeyData KD_Gxi = new KeyData('ξ');
	private static final KeyData KD_Gomicron = new KeyData('ο');
	private static final KeyData KD_Gpi = new KeyData('π');
	private static final KeyData KD_Grho = new KeyData('ρ');
	//final KeyData KD_sigma = new KeyData('σ');
	private static final KeyData KD_Gsigma = new KeyData('σ');
	//private static final KeyData KD_join(WHITESPACE = Gsigma) = KD_sigma); // To prevent the nexy firing when just the letter σ.
	//private static final KeyData KD_join(Gsigma = WHITESPACE) = KD_BACKSPACE = new KeyData('ς') = KD_WHITESPACE); // Sigma at the end of a word.
	private static final KeyData KD_Gtau = new KeyData('τ');
	private static final KeyData KD_Gupsilon = new KeyData('υ');
	private static final KeyData KD_Gphi = new KeyData('φ');
	private static final KeyData KD_Gchi = new KeyData('χ');
	private static final KeyData KD_Gpsi = new KeyData('ψ');
	private static final KeyData KD_Gomega = new KeyData('ω');
	private static final KeyData KD_GALPHA = new KeyData('Α');
	private static final KeyData KD_GBETA = new KeyData('Β');
	private static final KeyData KD_GGAMMA = new KeyData('Γ');
	private static final KeyData KD_GDELTA = new KeyData('Δ');
	private static final KeyData KD_GEPSILON = new KeyData('Ε');
	private static final KeyData KD_GZETA = new KeyData('Ζ');
	private static final KeyData KD_GETA = new KeyData('Η');
	private static final KeyData KD_GTHETA = new KeyData('Θ');
	private static final KeyData KD_GIOTA = new KeyData('Ι');
	private static final KeyData KD_GKAPPA = new KeyData('Κ');
	private static final KeyData KD_GLAMBDA = new KeyData('Λ');
	private static final KeyData KD_GMU = new KeyData('Μ');
	private static final KeyData KD_GNU = new KeyData('Ν');
	private static final KeyData KD_GXI = new KeyData('Ξ');
	private static final KeyData KD_GOMICRON = new KeyData('Ο');
	private static final KeyData KD_GPI = new KeyData('Π');
	private static final KeyData KD_GRHO = new KeyData('Ρ');
	private static final KeyData KD_GSIGMA = new KeyData('Σ');
	private static final KeyData KD_GTAU = new KeyData('Τ');
	private static final KeyData KD_GUPSILON = new KeyData('Υ');
	private static final KeyData KD_GPHI = new KeyData('Φ');
	private static final KeyData KD_GCHI = new KeyData('Χ');
	private static final KeyData KD_GPSI = new KeyData('Ψ');
	private static final KeyData KD_GOMEGA = new KeyData('Ω');

	// PUNCTUATION
	private static final KeyData KD_COMMA = new KeyData(',', KeyEvent.VK_COMMA);
	private static final KeyData KD_FULLSTOP = new KeyData('.', KeyEvent.VK_PERIOD);
	private static final KeyData KD_PLUS = new KeyData('+', KeyEvent.VK_PLUS);
	private static final KeyData KD_MINUS = new KeyData('-', KeyEvent.VK_MINUS);
	private static final KeyData KD_ASTERISK = new KeyData('*', KeyEvent.VK_ASTERISK);
	private static final KeyData KD_FORWARD_SLASH = new KeyData('/', KeyEvent.VK_SLASH);
	private static final KeyData KD_BACK_SLASH = new KeyData('\\', KeyEvent.VK_BACK_SLASH);
	private static final KeyData KD_EQUALS = new KeyData('=', KeyEvent.VK_EQUALS);
	private static final KeyData KD_LESS_THAN = new KeyData('<', true);
	private static final KeyData KD_GREATER_THAN = new KeyData('>', true);
	private static final KeyData KD_COLON = new KeyData(':', KeyEvent.VK_COLON, true);
	private static final KeyData KD_QUOTE = new KeyData('"', true);

	// NUMBERS
	private static final KeyData KD_0 = new KeyData('0');
	private static final KeyData KD_1 = new KeyData('1');
	private static final KeyData KD_2 = new KeyData('2');
	private static final KeyData KD_3 = new KeyData('3');
	private static final KeyData KD_4 = new KeyData('4');
	private static final KeyData KD_5 = new KeyData('5');
	private static final KeyData KD_6 = new KeyData('6');
	private static final KeyData KD_7 = new KeyData('7');
	private static final KeyData KD_8 = new KeyData('8');
	private static final KeyData KD_9 = new KeyData('9');

	// COMBING CHARS KEYDATA
	private static final KeyData KD_ACUTE = new KeyData('\u0301');
	private static final KeyData KD_CARON = new KeyData('\u030C');
	private static final KeyData KD_BREVE = new KeyData('\u0306');
	private static final KeyData KD_CEDILLA = new KeyData('\u0327');
	private static final KeyData KD_CIRCUMFLEX = new KeyData('\u0302');
	private static final KeyData KD_DIAERESIS = new KeyData('\u0308');
	private static final KeyData KD_GRAVE = new KeyData('\u0300');
	private static final KeyData KD_MACRON = new KeyData('\u0305');
	private static final KeyData KD_RING = new KeyData('\u030A');
	private static final KeyData KD_SOLIDUS = new KeyData('\u0338');
	private static final KeyData KD_STRIKETHROUGH = new KeyData('\u0336');
	private static final KeyData KD_TILDE_COMB = new KeyData('\u0303');
	private static final KeyData[] KD_LIGATURE = join(new KeyData('\uFE20'), new KeyData('\uFE21'));

	// QUALIFIERS
	private static final Integer DIGIT = 60;
	private static final Integer SHIFT8 = 8;
	private static final Integer SHIFT16 = 16;
	private static final Integer SHIFT24 = 24;
	private static final Integer SHIFT32 = 32;
	private static final Integer SHIFT40 = 40;
	private static final Integer SHIFT48 = 48;
	private static final Integer SHIFT56 = 56;
	private static final Integer SHIFT = SHIFT32;
	private static final Integer GRADE1 = SHIFT48;
	private static final Integer[] QUALIFIERS = join(DIGIT, SHIFT8, SHIFT16, SHIFT24, SHIFT32, SHIFT40, SHIFT48, SHIFT56);

	// PINCODES
	// SPECIAL
	private static final Integer ENTER = 128;
	private static final Integer[] SHIFT8_32 = join(SHIFT8, SHIFT32);
	private static final Integer SPACE = -KeyEvent.VK_SPACE;
	private static final Integer[] WHITESPACES = join(SPACE, ENTER);  // Ordered by most used.
	private static final Integer GROUP_OPEN = 35; // same as N2
	private static final Integer GROUP_CLOSE = 28;

	// LETTERS
	private static final Integer Aa = 1;
	private static final Integer Ab = 3;
	private static final Integer Ac = 9;
	private static final Integer Ad = 25;
	private static final Integer Ae = 17;
	private static final Integer Af = 11;
	private static final Integer Ag = 27;
	private static final Integer Ah = 19;
	private static final Integer Ai = 10;
	private static final Integer Aj = 26;
	private static final Integer Ak = 5;
	private static final Integer Al = 7;
	private static final Integer Am = 13;
	private static final Integer An = 29;
	private static final Integer Ao = 21;
	private static final Integer Ap = 15;
	private static final Integer Aq = 31;
	private static final Integer Ar = 23;
	private static final Integer As = 14;
	private static final Integer At = 30;
	private static final Integer Au = 37;
	private static final Integer Av = 39;
	private static final Integer Aw = 58;
	private static final Integer Ax = 45;
	private static final Integer Ay = 61;
	private static final Integer Az = 53;
	private static final HashMap<Integer, KeyData[][]> ALPHABET = new HashMap<Integer, KeyData[][]>();
	static {
		ALPHABET.put(Aa, link(KD_Aa, KD_AA));
		ALPHABET.put(Ab, link(KD_Ab, KD_AB));
		ALPHABET.put(Ac, link(KD_Ac, KD_AC));
		ALPHABET.put(Ad, link(KD_Ad, KD_AD));
		ALPHABET.put(Ae, link(KD_Ae, KD_AE));
		ALPHABET.put(Af, link(KD_Af, KD_AF));
		ALPHABET.put(Ag, link(KD_Ag, KD_AG));
		ALPHABET.put(Ah, link(KD_Ah, KD_AH));
		ALPHABET.put(Ai, link(KD_Ai, KD_AI));
		ALPHABET.put(Aj, link(KD_Aj, KD_AJ));
		ALPHABET.put(Ak, link(KD_Ak, KD_AK));
		ALPHABET.put(Al, link(KD_Al, KD_AL));
		ALPHABET.put(Am, link(KD_Am, KD_AM));
		ALPHABET.put(An, link(KD_An, KD_AN));
		ALPHABET.put(Ao, link(KD_Ao, KD_AO));
		ALPHABET.put(Ap, link(KD_Ap, KD_AP));
		ALPHABET.put(Aq, link(KD_Aq, KD_AQ));
		ALPHABET.put(Ar, link(KD_Ar, KD_AR));
		ALPHABET.put(As, link(KD_As, KD_AS));
		ALPHABET.put(At, link(KD_At, KD_AT));
		ALPHABET.put(Au, link(KD_Au, KD_AU));
		ALPHABET.put(Av, link(KD_Av, KD_AV));
		ALPHABET.put(Aw, link(KD_Aw, KD_AW));
		ALPHABET.put(Ax, link(KD_Ax, KD_AX));
		ALPHABET.put(Ay, link(KD_Ay, KD_AY));
		ALPHABET.put(Az, link(KD_Az, KD_AZ));
	}
	private static final int LOWER = 0;
	private static final int UPPER = 1;
	private static final int LEFT = 0;
	private static final int RIGHT = 1;

	// SIMPLE PUNCTUATION
	private static final Integer APOSTROPHE = 4;
	private static final Integer COLON = 18;
	private static final Integer COMMA = 2;
	private static final Integer EXCLAMATION = 22;
	private static final Integer FULLSTOP = 50;
	private static final Integer HYPHEN = 36;
	private static final Integer PRIME = 54;
	private static final Integer QUESTION = 38;
	private static final Integer QUESTION_INVERTED = 52;
	private static final Integer SEMICOLON = 6;

	// LIGATURES
	private static final HashMap<Integer[], KeyData[][]> LIGATURES = new HashMap<Integer[], KeyData[][]>();
	static {
		LIGATURES.put(join(Aa, Ae), link(KD_Lae, KD_LAE));
		LIGATURES.put(join(Af, Af), link(KD_Lff, null));
		LIGATURES.put(join(Af, Af, Ai), link(KD_Lffi, null));
		LIGATURES.put(join(Af, Af, Al), link(KD_Lffl, null));
		LIGATURES.put(join(Af, Ai), link(KD_Lfi, null));
		LIGATURES.put(join(Af, Al), link(KD_Lfl, null));
		LIGATURES.put(join(Af, At), link(KD_Lft, null));
		LIGATURES.put(join(Ai, Aj), link(KD_Lij, KD_LIJ));
		LIGATURES.put(join(Ao, Ae), link(KD_Loe, KD_LOE));
		LIGATURES.put(join(At, Ah), link(KD_Lth, KD_LTH));
		LIGATURES.put(join(As, At), link(KD_Lst, null));
		LIGATURES.put(join(Au, Ae), link(KD_Lue, null));
	}

	// NUMBERS
	private static final Integer N1 = 33;
	private static final Integer N2 = 35;
	private static final Integer N3 = 41;
	private static final Integer N4 = 57;
	private static final Integer N5 = 49;
	private static final Integer N6 = 43;
	private static final Integer N7 = 59;
	private static final Integer N8 = 51;
	private static final Integer N9 = 42;
	private static final Integer N0 = 63;

	private static final Integer[] N_TEN = join(N1, N2, N3, N4, N5, N6, N7, N8, N9, N0);
	private static final Integer[] D_TEN = join(Aa, Ab, Ac, Ad, Ae, Af, Ag, Ah, Ai, Aj);
	private static final KeyData[] KD_TEN = join(KD_1, KD_2, KD_3, KD_4, KD_5, KD_6, KD_7, KD_8, KD_9, KD_0);

	// MUSIC
	//private static final Integer[] NATURAL = join(DIGIT, N1); // No Arial char
	//private static final Integer[] FLAT = join(DIGIT, GROUP_OPEN); // No Arial char
	private static final Integer[] SHARP = join(DIGIT, N3);

	// COMPLEX PUNCTUATION
	private static final Integer[] DOUBLE_PRIME = join(PRIME, PRIME);

	// GROUP PUNCTUATION
	private static final Integer[] ANGLE_BRACKET_OPEN = join(SHIFT8, GROUP_OPEN);
	private static final Integer[] ANGLE_BRACKET_CLOSE = join(SHIFT8, GROUP_CLOSE);
	private static final Integer[] CURLY_BRACKET_OPEN = join(SHIFT56, GROUP_OPEN);
	private static final Integer[] CURLY_BRACKET_CLOSE = join(SHIFT56, GROUP_CLOSE);
	private static final Integer[] ROUND_BRACKET_OPEN = join(SHIFT16, GROUP_OPEN);
	private static final Integer[] ROUND_BRACKET_CLOSE = join(SHIFT16, GROUP_CLOSE);
	private static final Integer[] SQUARE_BRACKET_OPEN = join(SHIFT40, GROUP_OPEN);
	private static final Integer[] SQUARE_BRACKET_CLOSE = join(SHIFT40, GROUP_CLOSE);

	// SHIFT 8 / CURRENCY
	private static final Integer[] AMPERSAND = join(SHIFT8, 47);
	private static final Integer[] AT_SIGN = join(SHIFT8, Aa);
	private static final Integer[] CARET = join(SHIFT8, 34);
	private static final Integer[] CENT = join(SHIFT8, Ac);
	private static final Integer[] DOLLAR = join(SHIFT8, As);
	private static final Integer[] EURO = join(SHIFT8, Ae);
	private static final Integer[] FRANC = join(SHIFT8, Af);
	private static final Integer[] GBP = join(SHIFT8, Al);
	//private static final Integer[] GRREATER_THAN = ANGLE_BRACKET_CLOSE;
	//private static final Integer[] LESS_THAN = ANGLE_BRACKET_OPEN;
	private static final Integer[] NAIRA = join(SHIFT8, An);
	private static final Integer[] TILDE = join(SHIFT8, 20);
	private static final Integer[] YEN = join(SHIFT8, Ay);
	// SHIFT 8 - COMBINING CHARACTERS
	private static final Integer[] BREVE = join(SHIFT8, 44);
	private static final Integer[] MACRON = join(SHIFT8, HYPHEN);
	private static final Integer[] SOLIDUS = join(SHIFT8, N1);
	private static final Integer[] STRIKETHROUGH = join(SHIFT8, COLON);
	// SHIFT 8 -> SHIFT 32
	private static final Integer[] DAGGER = join(SHIFT8_32, N4);
	private static final Integer[] DOUBLE_DAGGER = join(SHIFT8_32, N7);

	// SHIFT 16 / MATHS
	private static final Integer[] ASTERISK = join(SHIFT16, 20);
	private static final Integer[] DITTO = join(SHIFT16, 2);
	private static final Integer[] DIVIDE = join(SHIFT16, 12);
	private static final Integer[] EQUALS = join(SHIFT16, PRIME);
	private static final Integer[] MINUS = join(SHIFT16, HYPHEN);
	private static final Integer[] MULTIPLY = join(SHIFT16, QUESTION);
	private static final Integer[] PLUS = join(SHIFT16, EXCLAMATION);

	// SHIFT 24
	private static final Integer[] COPYRIGHT = join(SHIFT24, Ac);
	private static final Integer[] DEGREES = join(SHIFT24, Aj);
	private static final Integer[] PARAGRAPH = join(SHIFT24, Ap);
	private static final Integer[] QUOTE_OPEN = join(SHIFT24, QUESTION);
	private static final Integer[] QUOTE_CLOSE = join(SHIFT24, QUESTION_INVERTED);
	private static final Integer[] REGISTERED = join(SHIFT24, Ar);
	private static final Integer[] SECTION = join(SHIFT24, As);
	private static final Integer[] TRADEMARK = join(SHIFT24, At);
	private static final Integer[] FEMALE = join(SHIFT24, Ax);
	private static final Integer[] MALE = join(SHIFT24, Ay);
	private static final Integer[] ENG = join(SHIFT24, An);
	// SHIFT 24 - COMBINING CHARACTERS
	private static final Integer[] ACUTE = join(SHIFT24, 12);
	private static final Integer[] CARON = join(SHIFT24, 44);
	private static final Integer[] CEDILLA = join(SHIFT24, 47);
	private static final Integer[] CIRCUMFLEX = join(SHIFT24, N3);
	private static final Integer[] DIAERESIS = join(SHIFT24, COLON);
	private static final Integer[] GRAVE = join(SHIFT24, N1);
	private static final Integer[] RING = join(SHIFT24, N6);
	private static final Integer[] TILDE_COMB = join(SHIFT24, N7);
	private static final Integer[] LIGATURE = join(SHIFT24, EXCLAMATION);

	// SHIFT 40
	private static final Integer[] PERCENT = join(SHIFT40, QUESTION_INVERTED);
	private static final Integer[] UNDERSCORE = join(SHIFT40, HYPHEN);

	// SHIFT 56
	private static final Integer[] ANGLE_QUOTE_OPEN = join(SHIFT56, QUESTION);
	private static final Integer[] ANGLE_QUOTE_CLOSE = join(SHIFT56, QUESTION_INVERTED);
	private static final Integer[] BACK_SLASH = join(SHIFT56, N1);
	private static final Integer[] BULLET = join(SHIFT56, FULLSTOP);
	private static final Integer[] FORWARD_SLASH = join(SHIFT56, 12);
	private static final Integer[] NUMBER = join(SHIFT56, N4);
	private static final Integer[] SCHWA = join(SHIFT56, 34);

	private static final HashMap<Integer[], KeyData[]> MODIFIERS = new HashMap<Integer[], KeyData[]>();
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
	private static final HashMap<Integer[], KeyData[][]> GREEK = new HashMap<Integer[], KeyData[][]>();
	static {
		GREEK.put(Galpha, link(KD_Galpha, KD_GALPHA));
		GREEK.put(Gbeta, link(KD_Gbeta, KD_GBETA));
		GREEK.put(Ggamma, link(KD_Ggamma, KD_GGAMMA));
		GREEK.put(Gdelta, link(KD_Gdelta, KD_GDELTA));
		GREEK.put(Gepsilon, link(KD_Gepsilon, KD_GEPSILON));
		GREEK.put(Gzeta, link(KD_Gzeta, KD_GZETA));
		GREEK.put(Geta, link(KD_Geta, KD_GETA));
		GREEK.put(Gtheta, link(KD_Gtheta, KD_GTHETA));
		GREEK.put(Giota, link(KD_Giota, KD_GIOTA));
		GREEK.put(Gkappa, link(KD_Gkappa, KD_GKAPPA));
		GREEK.put(Glambda, link(KD_Glambda, KD_GLAMBDA));
		GREEK.put(Gmu, link(KD_Gmu, KD_GMU));
		GREEK.put(Gnu, link(KD_Gnu, KD_GNU));
		GREEK.put(Gxi, link(KD_Gxi, KD_GXI));
		GREEK.put(Gomicron, link(KD_Gomicron, KD_GOMICRON));
		GREEK.put(Gpi, link(KD_Gpi, KD_GPI));
		GREEK.put(Grho, link(KD_Grho, KD_GRHO));
		GREEK.put(Gsigma, link(KD_Gsigma, KD_GSIGMA));
		GREEK.put(Gtau, link(KD_Gtau, KD_GTAU));
		GREEK.put(Gupsilon, link(KD_Gupsilon, KD_GUPSILON));
		GREEK.put(Gphi, link(KD_Gphi, KD_GPHI));
		GREEK.put(Gchi, link(KD_Gchi, KD_GCHI));
		GREEK.put(Gpsi, link(KD_Gpsi, KD_GPSI));
		GREEK.put(Gomega, link(KD_Gomega, KD_GOMEGA));
	}

	private static final String[] DEFAULT_STANDALONES = {
		"default" /*name*/, "" /*"" if same for lower and upper case*/,
		"", "but", "can", "do", "every", "from", "go", "have", "", "just", "knowledge", "like", "more",
		"not", "", "people", "quite", "rather", "so", "that", "us", "very", "will", "it", "you", "as"
	};

	private static final String[] JAVA_STANDALONES = {
		"java", "U",
		"new", "boolean", "char", "double", "enum", "float", "switch", "case", "int", "", "break", "class", "",
		"null", "for", "private", "", "return", "static", "this", "public", "void", "while", "if", "else", "final",
		"ArrayList", "Boolean", "Character", "Double", "", "Float", "Logger", "HashMap", "Integer", "", "", "List", "",
		"", "@Override", "", "", "Arrays", "String", "T", "", "", "", "", "System", ""
	};

	private static final HashMap<Integer, MapData> BRAILLE_MAP = new HashMap<Integer, MapData>();
	static {
		populateBrailleMap();
	}
}