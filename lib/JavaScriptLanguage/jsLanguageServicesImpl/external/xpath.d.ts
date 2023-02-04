// https://gist.github.com/13xforever/cb24b9e2f3335c296eb2
declare module ActiveX {
    export interface IXMLDOMNode {
        attributes: IXMLDOMNamedNodeMap;
        baseName?: string;
        childNodes: IXMLDOMNodeList;
        dataType?: string;
        definition?: IXMLDOMNode;
        firstChild: IXMLDOMNode;
        lastChild: IXMLDOMNode;
        namespaceURI?: string;
        nextSibling: IXMLDOMNode;
        nodeName: string;
        nodeType: IXMLDOMNodeType;
        nodeTypedValue?: any;
        nodeTypeString?: string;
        nodeValue: any;
        ownerDocument: IXMLDOMDocument;
        parentNode: IXMLDOMNode;
        parsed?: boolean;
        prefix?: string;
        previousSibling: IXMLDOMNode;
        specified?: boolean;
        text?: string;
        xml?: string;

        appendChild(newChild: IXMLDOMNode): IXMLDOMNode;
        cloneNode(deep: boolean): IXMLDOMNode;
        hasChildNodes(): boolean;
        insertBefore(newChild: IXMLDOMNode, refChild: IXMLDOMNode): IXMLDOMNode; //todo: refChild supposed to be variant
        removeChild(childNode: IXMLDOMNode): IXMLDOMNode;
        replaceChild(newChild: IXMLDOMNode, oldChild: IXMLDOMNode): IXMLDOMNode;
        save?(destination: any): void;
        selectNodes?(expression: string): IXMLDOMNodeList;
        selectSingleNode?(query: string): IXMLDOMNode;
        transformNode?(stylesheet: IXMLDOMNode): string;
        transformNodeToObject?(stylesheet: IXMLDOMNode, outputObject: any): void;
    }

    export interface IXMLDOMDocument extends IXMLDOMNode {
        async?: boolean;
        doctype: IXMLDOMDocumentType;
        documentElement: IXMLDOMElement;
        implementation: IXMLDOMImplementation;
        parseError?: IXMLDOMParseError;
        preserveWhiteSpace?: boolean;
        readyState?: XmlDocumentReadyState;
        resolveExternals?: boolean;
        url?: string;
        validateOnParse?: boolean;

        abort?(): void;
        createAttribute(name: string): IXMLDOMAttribute;
        createCDATASection(data: string): IXMLDOMCDATASection;
        createComment(data: string): IXMLDOMComment;
        createDocumentFragment(): IXMLDOMDocumentFragment;
        createElement(tagName: string): IXMLDOMElement;
        createEntityReference(name: string): IXMLDOMEntityReference;
        createNode?(type: IXMLDOMNodeType|string, name: string, namespaceURI: string): IXMLDOMNode;
        createProcessingInstruction(target: string, data: string): IXMLDOMProcessingInstruction;
        createTextNode(data: string): IXMLDOMText;
        getElementsByTagName(tagName: string): IXMLDOMNodeList;
        load?(xmlSource: any): boolean;
        loadXML?(bstrXML: string): boolean;
        nodeFromID?(idString: string): IXMLDOMNode;

        ondataavailable?: () => void;
        onreadystatechange?: () => void;
        ontransformnode?: (nodeCode: IXMLDOMNode, nodeData: IXMLDOMNode) => boolean;
    }

    export interface IXMLDOMElement extends IXMLDOMNode {
        tagName: string;

        getAttribute(name: string): string;
        getAttributeNode(name: string): IXMLDOMAttribute;
        getElementsByTagName(tagName: string): IXMLDOMNodeList;
        normalize(): void;
        removeAttribute(name: string): void;
        removeAttributeNode(attribute: IXMLDOMAttribute): IXMLDOMAttribute;
        setAttribute(name: string, value: any): void;
        setAttributeNode(attribute: IXMLDOMAttribute): IXMLDOMAttribute;
    }

    export interface IXMLDOMCharacterData extends IXMLDOMNode {
        data: string;
        length: number;

        appendData(data: string): void;
        deleteData(offset: number, count: number): void;
        insertData(offset: number, data: string): void;
        replaceData(offset: number, count: number, data: string): void;
        substringData(offset: number, count: number): string;
    }

    export interface IXMLDOMDocumentType extends IXMLDOMNode {
        entities: IXMLDOMNamedNodeMap;
        name: string;
        notations: IXMLDOMNamedNodeMap;
    }

    export interface IXMLDOMAttribute extends IXMLDOMNode {
        name: string;
        value: any;
    }

    export interface IXMLDOMProcessingInstruction extends IXMLDOMNode {
        data: string;
        target: string;
    }

    export interface IXMLDOMDocumentFragment extends IXMLDOMNode {
    }

    export interface IXMLDOMEntityReference extends IXMLDOMNode {
    }

    export interface IXMLDOMDocument2 extends IXMLDOMDocument {
        namespaces: IXMLDOMSchemaCollection;
        schemas: IXMLDOMSchemaCollection;

        getProperty(name: string): any;
        setProperty(name: string, value: any): void;
        validate(): IXMLDOMParseError;
    }

    export interface IXMLDOMText extends IXMLDOMCharacterData {
        splitText(offset: number): IXMLDOMText;
    }

    export interface IXMLDOMComment extends IXMLDOMCharacterData {
    }

    export interface IXMLDOMCDATASection extends IXMLDOMText {
    }

    export interface IXMLDOMNodeList {
        length: number;

        item(index: number): IXMLDOMNode;
        nextNode?(): IXMLDOMNode;
        reset?(): void;
    }

    export interface IXMLDOMNamedNodeMap {
        length: number;

        getNamedItem(name: string): IXMLDOMNode;
        getQualifiedItem?(baseName: string, namespaceURI: string): IXMLDOMNode;
        item(index: number): IXMLDOMNode;
        nextNode?(): IXMLDOMNode;
        removeNamedItem(name: string): IXMLDOMNode;
        removeQualifiedItem(baseName: string, namespaceURI: string): IXMLDOMNode;
        reset(): void;
        setNamedItem(newItem: IXMLDOMNode): IXMLDOMNode;
    }

    export interface IXMLDOMImplementation {
        hasFeature(feature: string, version: string): boolean;
    }

    export interface IXMLDOMParseError {
        errorCode: number;
        filepos: number;
        line: number;
        linepos: number;
        reason: string;
        srcText: string;
        url: string;
    }

    export interface IXMLDOMSchemaCollection {
        length: number;
        namespaceURI(index: number): string;
        validateOnLoad: boolean;

        add(namespaceURI: string, schema: string|IXMLDOMNode): void;
        addCollection(collection: IXMLDOMSchemaCollection): void;
        get(namespaceURI: string): IXMLDOMNode;
        getDeclaration(node: IXMLDOMNode): ISchemaItem;
        getSchema(namespaceURI: string): ISchema;
        remove(namespaceURI: string): void;
        validate(): void;
    }

    export interface IXMLDOMSchemaCollection2 extends IXMLDOMSchemaCollection {
        validate(): boolean;
    }

    export interface ISchema {
        attributeGroups: ISchemaItemCollection;
        attributes: ISchemaItemCollection;
        elements: ISchemaItemCollection;
        modelGroups: ISchemaItemCollection;
        notations: ISchemaItemCollection;
        schemaLocations: ISchemaStringCollection;
        targetNamespace: string;
        types: ISchemaItemCollection;
        version: string;
    }

    export interface ISchemaItem {
        id: string;
        itemType: SOMItemType;
        name: string;
        namespaceURI: string;
        schema: ISchema;
        unhandledAttributes: ISAXAttributes;

        writeAnnotation(...annotationSink: any[]): boolean;
    }

    export interface ISchemaItemCollection {
        item(index: number): ISchemaItem;
        length: number;

        itemByName(name: string): ISchemaItem;
        itemByQName(name: string, namespaceURI: string): ISchemaItem;
    }

    export interface ISchemaStringCollection {
        item(index: number): string;
        length: number;
    }

    export interface ISAXAttributes {
        length: number;

        getIndexFromName(uri: string, localName: string): number;
        getIndexFromQName(qName: string): number;
        getLength(): number;
        getLocalName(index: number): string;
        getName(index: number): any; //uri, localName, qName
        getQName(index: number): string;
        getType(index: number): string;
        getTypeFromName(uri: string, localName: string): string;
        getTypeFromQName(qName: string): string;
        getURI(index: number): string;
        getValue(index: number): string;
        getValueFromName(uri: string, localName: string): string;
        getValueFromQName(qName: string): string;
    }

    export interface IXSLProcessor {
        input: any;
        output: any;
        readyState: XslProcessorReadyState;
        startMode: string;
        startModeURI: string;
        stylesheet: IXMLDOMNode;
        ownerTemplate: IXSLTemplate;

        addObject(obj: any, namespaceURI: string): void;
        addParameter(baseName: string, parameter: number|boolean|string|IXMLDOMNode|IXMLDOMNodeList, namespaceURI?: string): void;
        reset(): void;
        setStartMode(mode: string, namespaceURI?: string): void;
        transform(): boolean;
    }

    export interface IXSLTemplate {
        stylesheet: IXMLDOMNode;
        createProcessor(): IXSLProcessor;
    }

    export const enum XmlDocumentReadyState {
        Loading = 1,
        Loaded = 2,
        Interactive = 3,
        Completed = 4,
    }

    export const enum IXMLDOMNodeType {
        NODE_ELEMENT = 1, //element
        NODE_ATTRIBUTE = 2, //attribute
        NODE_TEXT = 3, //text
        NODE_CDATA_SECTION = 4, //cdatasection
        NODE_ENTITY_REFERENCE = 5, //entityreference
        NODE_ENTITY = 6, //entity
        NODE_PROCESSING_INSTRUCTION = 7, //processinginstruction
        NODE_COMMENT = 8, //comment
        NODE_DOCUMENT = 9, //document
        NODE_DOCUMENT_TYPE = 10, //documenttype
        NODE_DOCUMENT_FRAGMENT = 11, //documentfragment
        NODE_NOTATION = 12, //notation
    }

    export const enum SOMItemType {
        SOMITEM_SCHEMA = 0x1000,
        SOMITEM_ATTRIBUTE = 0x1001,
        SOMITEM_ATTRIBUTEGROUP = 0x1002,
        SOMITEM_NOTATION = 0x1003,

        //Identity Constraints
        SOMITEM_IDENTITYCONSTRAINT = 0x1100,
        SOMITEM_KEY = 0x1101,
        SOMITEM_KEYREF = 0x1102,
        SOMITEM_UNIQUE = 0x1103,

        //Types
        SOMITEM_ANYTYPE = 0x2000, // also type mask
        SOMITEM_DATATYPE = 0x2100, // built-in type mask
        SOMITEM_DATATYPE_ANYTYPE = 0x2101,
        SOMITEM_DATATYPE_ANYURI = 0x2102,
        SOMITEM_DATATYPE_BASE64BINARY = 0x2103,
        SOMITEM_DATATYPE_BOOLEAN = 0x2104,
        SOMITEM_DATATYPE_BYTE = 0x2105,
        SOMITEM_DATATYPE_DATE = 0x2106,
        SOMITEM_DATATYPE_DATETIME = 0x2107,
        SOMITEM_DATATYPE_DAY = 0x2108,
        SOMITEM_DATATYPE_DECIMAL = 0x2109,
        SOMITEM_DATATYPE_DOUBLE = 0x210A,
        SOMITEM_DATATYPE_DURATION = 0x210B,
        SOMITEM_DATATYPE_ENTITIES = 0x210C,
        SOMITEM_DATATYPE_ENTITY = 0x210D,
        SOMITEM_DATATYPE_FLOAT = 0x210E,
        SOMITEM_DATATYPE_HEXBINARY = 0x210F,
        SOMITEM_DATATYPE_ID = 0x2110,
        SOMITEM_DATATYPE_IDREF = 0x2111,
        SOMITEM_DATATYPE_IDREFS = 0x2112,
        SOMITEM_DATATYPE_INT = 0x2113,
        SOMITEM_DATATYPE_INTEGER = 0x2114,
        SOMITEM_DATATYPE_LANGUAGE = 0x2115,
        SOMITEM_DATATYPE_LONG = 0x2116,
        SOMITEM_DATATYPE_MONTH = 0x2117,
        SOMITEM_DATATYPE_MONTHDAY = 0x2118,
        SOMITEM_DATATYPE_NAME = 0x2119,
        SOMITEM_DATATYPE_NCNAME = 0x211A,
        SOMITEM_DATATYPE_NEGATIVEINTEGER = 0x211B,
        SOMITEM_DATATYPE_NMTOKEN = 0x211C,
        SOMITEM_DATATYPE_NMTOKENS = 0x211D,
        SOMITEM_DATATYPE_NONNEGATIVEINTEGER = 0x211E,
        SOMITEM_DATATYPE_NONPOSITIVEINTEGER = 0x211F,
        SOMITEM_DATATYPE_NORMALIZEDSTRING = 0x2120,
        SOMITEM_DATATYPE_NOTATION = 0x2121,
        SOMITEM_DATATYPE_POSITIVEINTEGER = 0x2122,
        SOMITEM_DATATYPE_QNAME = 0x2123,
        SOMITEM_DATATYPE_SHORT = 0x2124,
        SOMITEM_DATATYPE_STRING = 0x2125,
        SOMITEM_DATATYPE_TIME = 0x2126,
        SOMITEM_DATATYPE_TOKEN = 0x2127,
        SOMITEM_DATATYPE_UNSIGNEDBYTE = 0x2128,
        SOMITEM_DATATYPE_UNSIGNEDINT = 0x2129,
        SOMITEM_DATATYPE_UNSIGNEDLONG = 0x212A,
        SOMITEM_DATATYPE_UNSIGNEDSHORT = 0x212B,
        SOMITEM_DATATYPE_YEAR = 0x212C,
        SOMITEM_DATATYPE_YEARMONTH = 0x212D,
        SOMITEM_SIMPLETYPE = 0x2200,
        SOMITEM_COMPLEXTYPE = 0x2400,
        SOMITEM_PARTICLE = 0x4000, // particle mask
        SOMITEM_ANY = 0x4001,
        SOMITEM_ANYATTRIBUTE = 0x4002,
        SOMITEM_ELEMENT = 0x4003,
        SOMITEM_GROUP = 0x4100, // group mask
        SOMITEM_ALL = 0x4101,
        SOMITEM_CHOICE = 0x4102,
        SOMITEM_SEQUENCE = 0x4103,
        SOMITEM_EMPTYPARTICLE = 0x4104,
        SOMITEM_NULL = 0x0800, // null items
        SOMITEM_NULL_TYPE = 0x2800,
        SOMITEM_NULL_ANY = 0x4801,
        SOMITEM_NULL_ANYATTRIBUTE = 0x4802,
        SOMITEM_NULL_ELEMENT = 0x4803,
    }

    export const enum XslProcessorReadyState {
        READYSTATE_UNINITIALIZED = 0,
        READYSTATE_LOADING = 1,
        READYSTATE_LOADED = 2,
        READYSTATE_INTERACTIVE = 3,
        READYSTATE_COMPLETE = 4,
    }
}