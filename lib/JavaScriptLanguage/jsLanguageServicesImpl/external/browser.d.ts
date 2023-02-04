declare const NaN: number;
declare const Infinity: number;

declare module Event {
    export const NONE: number;
    /**@deprecated*/ export const ABORT: number;
    /**@deprecated*/ export const BLUR: number;
    /**@deprecated*/ export const CLICK: number;
    /**@deprecated*/ export const CHANGE: number;
    /**@deprecated*/ export const DBLCLICK: number;
    /**@deprecated*/ export const DRAGDROP: number;
    /**@deprecated*/ export const ERROR: number;
    /**@deprecated*/ export const FOCUS: number;
    /**@deprecated*/ export const KEYDOWN: number;
    /**@deprecated*/ export const KEYPRESS: number;
    /**@deprecated*/ export const KEYUP: number;
    /**@deprecated*/ export const LOAD: number;
    /**@deprecated*/ export const MOUSEDOWN: number;
    /**@deprecated*/ export const MOUSEMOVE: number;
    /**@deprecated*/ export const MOUSEOUT: number;
    /**@deprecated*/ export const MOUSEOVER: number;
    /**@deprecated*/ export const MOUSEUP: number;
    /**@deprecated*/ export const MOVE: number;
    /**@deprecated*/ export const RESET: number;
    /**@deprecated*/ export const RESIZE: number;
    /**@deprecated*/ export const SELECT: number;
    /**@deprecated*/ export const UNLOAD: number;
}

interface KeyboardEvent extends UIEvent {
    readonly isComposing: string;
}

interface Navigator extends Object, NavigatorID, NavigatorOnLine, NavigatorContentUtils, NavigatorStorageUtils, NavigatorGeolocation, MSNavigatorDoNotTrack, MSFileSaver, NavigatorBeacon, NavigatorConcurrentHardware, NavigatorUserMedia {
    /**@deprecated*/ readonly systemLanguage: string;
    /**@deprecated*/ readonly userLanguage: string;
}

interface HTMLStyleElement extends HTMLElement, LinkStyle {
    /**@deprecated*/ readonly styleSheet: StyleSheet;
}

interface Window {
    //overrides default behaviour for window.location (any is required for completion)
    readonly location: Location | string | any;
    
    /**@deprecated*/ captureEvents(eventType: number): void;
    /**@deprecated*/ releaseEvents(eventType: number): void;
    
    print():void;
}

interface Document {
    //overrides default behaviour for document.location (any is required for completion)
    readonly location: Location | string | any;
}

interface HTMLTextAreaElement {
    selectionDirection: string;
}

/**
 * @deprecated The  function was deprecated in JavaScript version 1.5. Use decodeURI() or decodeURIComponent() instead
 */
declare var unescape:any;

/**
 * @deprecated The function was deprecated in JavaScript version 1.5. Use encodeURI() or encodeURIComponent() instead
 */
declare var escape:any;

interface Promise<T> {
    finally?<U>(onFinally?: () => U | Promise<U>): Promise<U>;
}

/**
 * https://github.com/Microsoft/TypeScript/issues/22917
 */
declare function print(): void;

//unsupported properties:
// Object.prototype.__proto__
//window.opera
//document.selection
//Element.prototype.detachEvent
//Element.prototype.attachEvent
//Event.prototype.clientX
//Event.prototype.clientY
//Event.prototype.offsetX
//Event.prototype.offsetY
//Event.prototype.altKey
//Event.prototype.ctrlKey
//Event.prototype.repeat
//Event.prototype.shiftKey
//Event.prototype.keyCode
//Element.prototype.currentStyle
//Location.prototype.target
//document.namespaces
//RegExp.input;
//RegExp.rightContext
//RegExp.lastParen
//RegExp.leftContext
//RegExp.rightContext
//Element.prototype.setCapture()
//Element.prototype.releaseCapture()
//Element.prototype.clearAttributes()
//Element.prototype.mergeAttributes()
//Element.prototype.fireEvent() 
//document.createEventObject()
//Event.prototype.propertyName
//Element.prototype.isDisabled
//document.styleSheet
//window.showModalDialog
//sourceIndex
//doScroll
//window.execScript
//window.CollectGarbage
//getBookmark
//moveToBookmark
//document.documentMode