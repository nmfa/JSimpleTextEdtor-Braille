package com.anas.jsimpletexteditor;

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
        } else if (e.getID() == KeyEvent.KEY_RELEASED) {
            keyDown = false;
			log.info("KEYUP: " + e.getKeyChar() + ", " + e.getKeyCode());

			// Space will just get passed through.
			
        } else {
            // Ignore
            return;
        }
        boolean keyUp = !keyDown;

        // If keyUp then we send what we currently have.
        if (keyUp && lastKeyDown && brailleMap.containsKey(currentPins)) {
			log.info("PROCESS: " +  brailleMap.get(currentPins).keyChar + ", " + brailleMap.get(currentPins).keyCode);
            KeyEvent newE = new KeyEvent(e.getComponent(),
                                         KeyEvent.KEY_PRESSED,
                                         e.getWhen(),
                                         0,
                                         brailleMap.get(currentPins).keyCode,
                                         brailleMap.get(currentPins).keyChar);
            super.processKeyEvent(newE);
            newE = new KeyEvent(e.getComponent(),
                            	KeyEvent.KEY_TYPED,
                                e.getWhen(),
                                0,
                                KeyEvent.VK_UNDEFINED,
                                brailleMap.get(currentPins).keyChar);
            super.processKeyEvent(newE);
            newE = new KeyEvent(e.getComponent(),
                                KeyEvent.KEY_RELEASED,
                                e.getWhen(),
                                0,
                                brailleMap.get(currentPins).keyCode,
                                brailleMap.get(currentPins).keyChar);
			super.processKeyEvent(newE);
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
