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
}




public class TextAreaBraille extends JTextArea {
    private HashMap<Integer, KeyData> brailleMap = new HashMap<Integer, KeyData>();
    private HashMap<Integer, Integer> keyPinMap = new HashMap<Integer, Integer>();
    Logger log = Logger.getLogger("TextAreaBraille");

    private int currentPins = 0;
    private boolean lastKeyDown = false;
	private int shift = 0; // 0 = no shift, 1 = normal, 2 = word, 3 = Caps Lock

    TextAreaBraille() {
        super();
        populateBrailleMaps();
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
        boolean keyDown;
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            keyDown = true;
			log.info("KEYDOWN: " + e.getKeyChar() + ", " + e.getKeyCode());

			// Ignore Space
			if (e.getKeyCode() == 32) {
				return;
			}
        } else if (e.getID() == KeyEvent.KEY_RELEASED) {
            keyDown = false;
			log.info("KEYUP: " + e.getKeyChar() + ", " + e.getKeyCode());

			// Space will just get passed through.
			if (e.getKeyCode() == 32) {
				sendKeyEvents(e.getComponent(), e. getWhen(), brailleMap.get(-32));
				if (shift < 3) shift = 0;
			}

			// Shift
			if (currentPins == 32) {
				shift++;
				if (shift > 3) shift = 0;
				currentPins = 0;
				return;
			}
        } else {
            // Ignore
            return;
        }
        boolean keyUp = !keyDown;

        // If keyUp then we send what we currently have.
        if (keyUp && lastKeyDown && brailleMap.containsKey(currentPins)) {
			sendKeyEvents(e.getComponent(), e.getWhen(), brailleMap.get(currentPins));
			if (shift == 1) shift = 0;
		}

        Integer pin = keyPinMap.get(e.getKeyCode());
		if (pin != null) {
			if (keyDown) {
				currentPins = currentPins | pin;
			} else {
				currentPins = ~(~currentPins | pin);
			}
			log.info("CURRENT PINS: " + currentPins);
		}

        lastKeyDown = keyDown;

        //super.processKeyEvent(e);
    }


	private void sendKeyEvents(Component c, long when, KeyData kd) {
		char keyChar = kd.keyChar; // So that we don't alter the KeyData value in the map.
		int modifiers = 0;
		if (shift > 0) {
			modifiers = KeyEvent.SHIFT_DOWN_MASK;
			keyChar = Character.toUpperCase(keyChar);
		}

		log.info("SEND KEYEVENTS: " + kd.keyChar + ", " + kd.keyCode);
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
        brailleMap.put( 1, new KeyData('a'));
        brailleMap.put( 3, new KeyData('b'));
        brailleMap.put( 9, new KeyData('c'));
        brailleMap.put(25, new KeyData('d'));
        brailleMap.put(17, new KeyData('e'));
        brailleMap.put(11, new KeyData('f'));
        brailleMap.put(27, new KeyData('g'));
        brailleMap.put(19, new KeyData('h'));
        brailleMap.put(10, new KeyData('i'));
        brailleMap.put(26, new KeyData('j'));
        brailleMap.put( 5, new KeyData('k'));
        brailleMap.put( 7, new KeyData('l'));
        brailleMap.put(13, new KeyData('m'));
        brailleMap.put(29, new KeyData('n'));
        brailleMap.put(21, new KeyData('o'));
        brailleMap.put(15, new KeyData('p'));
        brailleMap.put(31, new KeyData('q'));
        brailleMap.put(23, new KeyData('r'));
        brailleMap.put(14, new KeyData('s'));
        brailleMap.put(30, new KeyData('t'));
        brailleMap.put(37, new KeyData('u'));
        brailleMap.put(39, new KeyData('v'));
        brailleMap.put(58, new KeyData('w'));
        brailleMap.put(45, new KeyData('x'));
        brailleMap.put(61, new KeyData('y'));
        brailleMap.put(53, new KeyData('z'));

		// ASCII CHARACTERS. Stored under the negative of their keycode.
        brailleMap.put(-32, new KeyData(' '));

        keyPinMap.put(70, 1);   // F
        keyPinMap.put(68, 2);   // D
        keyPinMap.put(83, 4);   // S
        keyPinMap.put(74, 8);   // J
        keyPinMap.put(75, 16);  // K
        keyPinMap.put(76, 32);  // L
        keyPinMap.put(65, 64);  // A
        keyPinMap.put(59, 128); // ;
    }
}
