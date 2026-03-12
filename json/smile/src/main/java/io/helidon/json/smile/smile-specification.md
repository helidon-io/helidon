# Efficient JSON-compatible binary format: "Smile"

"Smile" is an efficient JSON-compatible binary data format, initially developed by Jackson JSON processor project team.
Its logical data model is same as that of JSON, so it can be considered a "Binary JSON" format.

For design on "Project Smile", which implements support for this format, see
[Smile format Design goals](smile-design-goals.md).

This page covers current data format specification; which is planned to eventually be standardized through a format process (most likely as IETF RFC).

## Document version

### Version history

* Current: 1.0.7 (26-Aug-2025)
    * Previous: 1.0.6 (18-Apr-2025)
* First official: 1.0.0 (12-Sep-2010)
* First draft: (24-Jun-2010)

### Update history

* 2025-08-26: Corrected explanation and example of 32-bit `float` encoding
    * Version 1.0.6 -> 1.0.7
* 2025-05-18: Explained existing (implement by Jackson codec) but previously undocumented requirement to skip Shared String/Key Name references ending with `0xFE` / `0xFF` bytes.
    * Version 1.0.5 -> 1.0.6
* 2022-02-20: Clarify handling of "unused" bits (see issue #17) primarily regarding encoding of floating-point numbers, but more generally for all unused bits.
* 2022-01-26: Important fix to encoding of 7-bit encoded (safe) binary, wrt padding of the last byte.
    * Version 1.0.4 -> 1.0.5
* 2021-03-18: Minor markup fixes, clarification to "simple literals, numbers" section
* 2020-11-19: Added notes on length indicatos for "Tiny" and "Short" String values (ASCII and Unicode) (contributed by @jviotti)
* 2020-11-16: Replacing accidental "Small ASCII" and "Small Unicode" references to canonical "Short ASCII" and "Short Unicode" (contributed by @jviotti)
* 2020-10-24: Minor clarification on encoding of 32-bit IEEE floating point value
* 2016-02-23: Minor clarification on acceptable lengths for long non-shared names (different minimum for ascii, non-ascii)
* 2014-05-21: Fixed a typo: end-of-string marker is `0xFC` and NOT `0xFE` as stated in one place.
* 2014-03-28: Added improvement ideas for possible chunked variants of binary data, Strings.
* 2013-05-12: Important document fix: value codes `0xE8` and `0xEC` were mixed in document (Java and C codecs implement correctly) -- bumped version to 1.0.4 to signify this clarification. (big thanks to `gbooker@github` for pointing out this discrepancy)
* 2013-03-06: Fixed a minor flaw in description of Zigzag encoding (multiply by two, not one)
* 2012-02-21: Added description of zigzag-encoding for VInts
* 2011-11-29: Clarified that values `0xF0` - `0xF7` are "reserved for future use" in value mode (but used in key mode)
* 2011-07-27: Fixed 2 erroneous references to "5 MSB" which should be "5 LSB" (thanks Pierre!)
* 2011-07-15: Formatting improvements; add a note about recommended MIME Type for Smile encoded data
* 2011-02-16: Fix a minor typo in section 2.3 ("Short Unicode names"); range `0xF8` - `0xFE` is NOT used for encoding
* 2010-09-12: Mark as 1.0.0; add some future plan notes.
* 2010-08-26: Rearrange token byte values to make better use of `0xF8` - `0xFF` area for framing.
* 2010-08-20: Remove references to initially planned in-frame compression; add one more header flag, extend version info to 4 bits

## External Considerations

### MIME Type

There is no formal or official MIME type registered for Smile content, but the current best practice (as of July 2011) is to use:

    application/x-jackson-smile

since this is used by multiple existing projects.

## High-level format

At high level, content encoded using this format consists of a simple sequence of sections, each of which consists of:

* A 4-byte header (described below) that can be used to identify content that uses the format, as well as its version and any per-section configuration settings there may be.
* Sequence (0 to N) of tokens that are properly nested (all start-object/start-array tokens are matched with equivalent close tokens) within sequence.
* Optional end marker, 0xFF, can be used: if encountered, it will be consider same as end-of-stream. This is added as a convenience feature to help with framing.

Header consists of:

* Constant byte #0: `0x3A` (ASCII ':')
* Constant byte #1: `0x29` (ASCII ')')
* Constant byte #2: `0x0A` (ASCII linefeed, '\n')
* Variable byte #3, consisting of bits:
    * Bits 4-7 (4 MSB): 4-bit version number; `0x00` for current version (note: it is possible that some bits may be reused if necessary)
    * Bits 3: Reserved
    * Bit 2 (mask `0x04`) Whether "raw binary" (unescaped 8-bit) values may be present in content
    * Bit 1 (mask `0x02`): Whether "shared String value" checking was enabled during encoding -- if header missing, default value of `false` must be assumed for decoding (meaning parser need not store decoded String values for back referencing)
    * Bit 0 (mask `0x01`): Whether "'shared property name" checking was enabled during encoding -- if header missing, default value of `true` must be assumed for decoding (meaning parser MUST store seen property names for possible back references)

And basically first 2 bytes form simple smiley and 3rd byte is a (Unix) linefeed: this to make command-line-tool based identification simple: choice of bytes is not significant beyond visual appearance. Fourth byte contains minimal versioning marker and additional configuration bits.

## Low-level Format

Each section described above consist of set of tokens that forms properly nested JSON value. Tokens are used in two basic modes: value mode (in which tokens are "value tokens"), and property-name mode ("key tokens"). Property-name mode is used within JSON Object values to denote property names, and alternates between name / value tokens.

Token lengths vary from a single byte (most common) to 9 bytes. In each case, first byte determines type, and additional bytes are used if and as indicated by the type byte. Type byte value ranges overlap between value and key tokens; but not all type bytes are legal in both modes.

Use of certain byte values is limited:

* Values `0xFD` through `0xFF` are not used as token type markers, key markers, or in values; with exception of optional raw binary data (which can contain any values). Instead they are used to:
    * `0xFF` can be used as logical data end marker; this use is intended to be compatible with Web Sockets usage
    * `0xFE` is reserved for future use, and not used for anything currently.
    * `0XFD` is used as type marker for raw binary data, to allow for uniquely identifying raw binary data sections (note too that content header will have to explicitly enable support; without this content can not contain raw binary data sections)
    * `0xFC` is used as String end-marker (similar to use of zero byte with C strings) for long Strings that do not use length prefix.
    * Since number encodings never use values `0xC0` - `0xFF`, and UTF-8 does not use values `0xF8` - `0xFF`, these are only uses within Smile format (except for possible raw binary data)
* Values `0xF8` - `0xFB` are only used for type tokens `START_ARRAY`, `END_ARRAY`, `START_OBJECT` and `END_OBJECT` (respectively); they are not used for String or numeric values of field names and can otherwise only occur in raw binary data sections.
* Value `0x00` has no specific handling (can occur in variable length numeric values, as UTF-8 null characters and so on).
* `0x3A` is not used as type byte in either mode, since it is the first byte of 4-byte header sequence, and may thus be encountered after value tokens (and although it can not occur within key mode, it is reserved to increase chances of detecting corrupted content)
    * Value can occur within many kinds of values (vints, String values)

### Tokens: general

Some general notes on tokens:

* (2022-02-20) Unused bits in encoded bytes:
    * SHOULD be encoded as `0` bits by encoder
    * MUST be ignored by decoders for purposes of decoding itself (MUST NOT affect result of decoding even if `1`)
    * MAY, however, be verified by decoder but if so MUST NOT fail decoding by default; decoders MAY however report non-compliant `1` bits as warnings
    * Decoders MAY additionally expose optional "strict" mode in which such non-compliant bit encoding does result in an error and decoding failure
* Strings are encoded using standard UTF-8 encoding; length is indicated either by using:
    * 6-bit byte length prefix, for lengths 1 - 63 (0 is not used since there is separate token)
    * End-of-String marker byte (`0xFC`) for variable length Strings.
* Integral numeric values up to Java long (64-bit) are handled using `ZigZag`-encoded VInts (see Appendix for details):
    * sequence of 1 to 10 bytes that can represent all 64-bit numbers.
    * VInts are big endian, meaning that most-significant bytes come first
    * All bytes except for the last one have their MSB clear, leaving 7 data bits
    * Last byte has its MSB (bit #7) set, but bit #6 NOT set (to avoid possibility of collision with `0xFF`), leaving 6 data bits.
    * This means that 2 byte VInt has 13 data bits, for example; and minimum number of bytes to represent a Java long (64 bits) is 10; 9 bytes would give 62 bits (8 * 7 + 6).
    * Signed VInt values are handled using "zigzag" encoding, where sign bit is shifted to be the least-significant bit, and value is shifted left by one (i.e. multiplied by two).
    * Unsigned VInts used as length indicators do NOT use zigzag encoding (since it is only needed to help with encoding of negative values)
    * "Unused" bits in the last encoded byte should be handled as per earlier general note: left as `0`
* Length indicators are done using VInts (for binary data, ("big") integer/decimal values)
    * All length indicators define _actual_ length of data; not possibly encoded length (in case of "safe" encoding, encoded data is longer, and that length can be calculated from payload data length)
    * "Unused" bits in the last encoded byte should be handled as per earlier general note: left as `0`
* Floating point values (IEEE 32 and 64-bit) are encoded using fixed-length big-endian (MSB first) encoding (7 bits used to avoid use of reserved bytes like `0xFF`):
    * Data is "right-aligned", meaning padding is prepended to the first byte (and specifically as MSB).
    * For example, the 32-bit float `29.9510` is encoded as `0x04 0x0F 0x3E 0x37 0x26`. We get to this encoding by taking the IEEE 764 32-bit binary representation of the number `29.9510` -- `0x41ef9ba6` -- and:
        1. writing the most-significant 4 bits (with 4-bit MSB padding),
        2. followed by next-MSB 7 bits, and
        3. repeating the process until encoding the entire bit-string (5 bytes for a 32-bit float).
    * As a result we get:
        1. `0x04' = '(29.9510 >> 28) & 0x7F`
        2. `0x0F` = `(29.9510 >> 21) & 0x7F`
        3. `0x3E` = `(29.9510 >> 14) & 0x7F`
        4. `0x37` = `(29.9510 >> 7) & 0x7F`
        5. `0x26` = `29.9510 & 0x7F`
    * "Unused" bits (4 most-significant) in the first encoded byte should be handled as per earlier general note: left as `0`
    * 64-bit `double` is handled similarly, so with sample value `-29.9510` we get:
        * raw 64-bits: `0xc03df374bc6a7efa`
        * encode in 10 data bytes (1 bit in first, 9 x 7 bits), MSB first
        * resulting bytes: `0x01 0x40 0x1e 0x7c 0x6e 0x4b 0x63 0x29 0x7d 0x7a`
    * Actual encoded value also has "type prefix byte -- `0x28` for `float` and `0x29` for `double`
* "Big" decimal/integer values use "safe" binary encoding
* "Safe" binary encoding simply uses 7 LSB (sign bit, MSB, is left as 0).
    * The last encoded byte contains 1 - 7 bits: if less than 7, data is "right-aligned", contained in Least-Significant Bits; there will be 0-6 MSB padding bits.
    * For example: when encoding 4 bytes (32 bits), the first 4 full (7-bit) encoded bytes (`0vvvvvvv`) -- ncoding 28 most-significant bits  -- are followed by one incomplete byte containing the last 4 value bits: `0000vvvv`.
    * NOTE: before version 1.0.5 above statement claimed incorrect alignment (claiming padding would be for the LSB of output byte; instead of LSB containing actual value bits)
    * "Unused" bits in the last encoded byte should be handled as per earlier general note: left as `0`.

### Tokens: value mode

Value is the default mode for tokens for main-level ("root") output context and JSON Array context. It is also used between JSON Object property name tokens (see next section).

Conceptually tokens are divided in 8 classes, class defined by 3 MSB of the first byte:

* `0x00` - `0x1F`: Short Shared Value String reference (single byte)
* `0x20` - `0x3F`: Simple literals, numbers
* `0x40` - `0x5F`: Tiny ASCII (1 - 32 bytes == chars)
* `0x60` - `0x7F`: Short ASCII (33 - 64 bytes == chars)
* `0x80` - `0x9F`: Tiny Unicode (2 - 33 bytes; <= 33 characters)
* `0xA0` - `0xBF`: Short Unicode (34 - 64 bytes; <= 64 characters)
* `0xC0` - `0xDF`: Small integers (single byte)
* `0xE0` - `0xFF`: Binary / Long text / structure markers (`0xF0` - `0xF7` is unused, reserved for future use -- but note, used in key mode)

These token class are are described below.

#### Token class: Short Shared Value String reference

Prefix: `0x00`; covers byte values `0x01` - `0x1F` (`0x00` not used as value type token)

* 5 LSB used to get reference value of 1 - 31; 0 is not used with this version (reserved for future use)
* Back reference resolved as explained in section 4.

#### Token class: Simple literals, numbers

Prefix: `0x20`; covers byte values `0x20` - `0x3F`, although not all values are used

* Literals (simple, non-structured)
    * `0x20`: "" (empty String)
    * `0x21`: null
    * `0x22` / `0x23`: `false` / `true`
* Numbers:
    * `0x24` - `0x27` Integral numbers; 2 LSB (`0x03`) contain subtype
        * `0x24` - 32-bit integer; zigzag encoded, 1 - 5 data bytes
        * `0x25` - 64-bit integer; zigzag encoded, 5 - 10 data bytes
        * `0x26` - `BigInteger`
            * Encoded as token indicator followed by 7-bit escaped binary (with Unsigned VInt (no-zigzag encoding) as length indicator) that represent magnitude value (byte array)
        * `0x27` - reserved for future use
    * `0x28` - `0x2B` floating point numbers; 2 LSB (`0x03`) contain subtype
        * `0x28`: 32-bit float
        * `0x29`: 64-bit double
        * `0x2A`: `BigDecimal`
            * Encoded as token indicator followed by zigzag encoded scale (32-bit), followed by 7-bit escaped binary (with Unsigned VInt (no-zigzag encoding) as length indicator) that represent magnitude value (byte array) of integral part.
        * `0x2B` - reserved for future use
    * Note that possible "unused" bits in the last encoded byte should be handled as per earlier general note: left as `0`, ignored on decoding.
* Reserved for future use, avoided (decoding error if found)
    * `0x2C` - `0x2F` reserved for future use (non-overlapping with keys)
    * `0x30` - `0x3F` overlapping with key mode and/or header (`0x3A`)

Rest of the possible values are reserved for future use and not used currently.

#### Token classes: Tiny ASCII, Short ASCII

Prefixes: `0x40`  / `0x60`; covers all byte values between `0x40` and `0x7F`.

* `0x40` - `0x5F`: Tiny ASCII
    * String with specified length; all bytes in ASCII range.
    * 5 LSB used to indicate lengths from 1 to 32 (bytes == chars)
    * **Note**: The character-length-length of the ASCII string is therefore what the 5 LSB encodes + 1
* `0x60` - `0x7F`: Short ASCII
    * String with specified length; all bytes in ASCII range
    * 5 LSB used to indicate lengths from 33 to 64 (bytes == chars)
    * **Note**: The character-length of the ASCII string is therefore what the 5 LSB encodes + 33

#### Token classes: Tiny Unicode, Short Unicode

Prefixes: `0x80`  / `0xA0`; covers all byte values between `0x80` and `0xBF`; except that `0x80` is not encodable (since there is no 1 byte long multi-byte-character String)

* `0x80` - `0x9F`
    * String with specified length; bytes NOT guaranteed to be in ASCII range
    * 5 LSB used to indicate _byte_ lengths from 2 to 33 (with character length possibly less due to multi-byte characters)
    * Length 1 can not be expressed, since only ASCII characters have single byte encoding (which means it should be encoded with "Tiny ASCII")
    * **Note**: The byte-length of the UTF-8 string is therefore what the 5 LSB encodes + 2 (different from the "Tiny ASCII" encoding)
* `0xA0` - `0xBF`
    * 5 LSB used to indicate _byte_ lengths from 34 to 65 (with character length possibly less due to multi-byte characters)
    * **Note**: The byte-length of the UTF-8 string is therefore what the 5 LSB encodes + 34 (different from the "Short ASCII" encoding)

#### Token class: Small integers

Prefix: `0xC0`; covers byte values `0xC0` - `0xDF`, all values used.

* Zigzag encoded
* 5 LSB used to get values from -16 to +15

#### Token class: Misc; binary / text / structure markers

Prefix: `0xE0`; covers byte values `0xE0` - `0xEF`, `0xF8` - `0xFF`: `0xF8` - `0xFF` not used with this format version (reserved for future use)

Note, too, that value `0x36` could be viewed as "real" `END_OBJECT`; but is not included here since it is only encountered in "key mode" (where you either get a key name, or `END_OBJECT` marker)

This class is further divided in 8 sub-section, using value of bits #2, #3 and #4 (0x1C) as follows:

* `0xE0`: Long (variable length) ASCII text
    * 2 LSB (`0x03`): reserved for future use
    * NOTE: these values are NOT back-referencable, so they do not participate in back-reference resolution (indexes/tables not updated)
* `0xE4`: Long (variable length) Unicode text
    * 2 LSB (`0x03`): reserved for future use
    * NOTE: these values are NOT back-referencable, so they do not participate in back-reference resolution (indexes/tables not updated)
* `0xE8`: Binary, 7-bit encoded
    * 2 LSB (`0x03`): reserved for future use
    * followed by VInt length indicator, then data in 7/8 encoding (only 7 LSB of each byte used [sign bit always 0]; 8 such bytes are used to encode 7 "raw" bytes)
    * Due to alignment the last byte may contain fewer than 7 bits: if so, the LSB bits contain data and up to 7 MSB may be left as 0 (the highest bit, sign bit, is always 0).
    * NOTE: before version 1.0.5, some documentation suggested padding would be for LSB -- this is NOT the case.
* `0xEC`: Shared String reference, Long
    * 2 LSB (`0x03`): used as 2 MSB of index
    * followed by byte used as 8 LSB of index
        * NOTE: this byte MUST NOT BE `0xFE` or `0xFF` -- generator MUST ensure avoidance (meaning that a small number of non-Shared Strings can not be referenced at all)
    * Resulting 10-bit index used as is; values 0-30 are not to be used (instead, Short reference must be used)
    * Back references are ONLY made to "short" and "tiny" ASCII/Unicode Strings, so generator and parser only need to retain references to these Strings and not "long" (aka variable length) Strings.
* `0xF0` - `0xF7`: not used, reserved for future use (NOTE: used in key mode)
* `0xF8` - `0xFB`: Structural markers
    * `0xF8`: `START_ARRAY`
    * `0xF9`: `END_ARRAY`
    * `0xFA`: `START_OBJECT`
    * `0xFB`: reserved in token mode (but is `END_OBJECT` in key mode) -- this just because object end marker comes as alternative to property name.
* `0xFC`: Used as end-of-String marker
* `0xFD`: Binary (raw)
    * followed by VInt length indicator, then raw data
* `0xFE`: reserved for future use
* `0xFF`: end-of-content marker (not used in content itself)

###  Tokens: key mode

Key mode tokens are only used within JSON Object values; if so, they alternate between value tokens (first a key token; followed by either single-value value token or multi-token JSON Object/Array value). A single token denotes end of JSON Object value; all the other tokens are used for expressing JSON Object property name.

Most tokens are single byte: exceptions are 2-byte "long shared String" token, and variable-length "long Unicode String" tokens.

Byte ranges are divides in 4 main sections (64 byte values each):

* `0x00` - `0x3F`: miscellaneous
    * `0x00` - `0x1F`: not used, reserved for future versions
    * `0x20`: Special constant name "" (empty String)
    * `0x21` - `0x2F`: reserved for future use (unused for now to reduce overlap between values)
    * `0x30` - `0x33`: "Long" shared key name reference (2 byte token); 2 LSBs of the first byte are used as 2 MSB of 10-bit reference (up to 1024) values to a shared name: second byte used for 8 LSB of 10-bit reference.
        * Note: combined values of 0 through 64 are reserved, since there is more optimal representation -- encoder is not to produce such "short long" values; decoder should check that these are not encountered. Future format versions may choose to use these for specific use.
        * NOTE: second byte MUST NOT BE `0xFE` or `0xFF` -- generator MUST ensure avoidance (meaning that a small number of non-Shared Names can not be referenced at all)
    * `0x34`: Long (not-yet-shared) Unicode name. Variable-length String; token byte is followed by 64 or more bytes, followed by end-of-String marker byte.
        * Note: encoding of Strings shorter than 56 bytes should NOT be done using this type: if such sequence is detected it MAY be considered an error. Further, for ASCII names, Strings with length of 56-64 should also use short String notation
    * `0x35` - `0x39`: not used, reserved for future versions
    * `0x3A`: Not used; would be part of header sequence (which is NOT allowed in key mode!)
    * `0x3B` - `0x3F`: not used, reserved for future versions
* `0x40` - `0x7F`: "Short" shared key name reference; names 0 through 63.
* `0x80` - `0xBF`: Short ASCII names
    * Names consisting of 1 - 64 bytes, all of which represent UTF-8 ASCII characters (MSB not set) -- special case to potentially allow faster decoding
* `0xC0` - `0xF7`: Short Unicode names
    * Names consisting of 2 - 57 bytes that can potentially contain UTF-8 multi-byte sequences: encoders are NOT required to guarantee there is one, but for decoding efficiency reasons are recommended to check (that is: decoders on many platforms will be able to handle ASCII-sequences more efficiently than general UTF-8 names)
* `0xF8` - `0xFA`: reserved (avoid overlap with `START_ARRAY`/`END_ARRAY`, `START_OBJECT`)
* `0xFB`: `END_OBJECT` marker
* `0xFC` - `0xFF`: reserved for framing, not used in key mode (used in value mode)

#### Resolved Shared String references

Shared Strings refer to already encoded/decoded key names or value strings. The method used for indicating which of "already seen" String values to use is designed to allow for:

* Efficient encoding AND decoding (without necessarily favoring either)
    * NOTE: data structures differ, however; encoder usually requires (hash) lookup whereas decoder can use simple index-accessible Array or List so typically encoder has somewhat higher overhead
* To allow keeping only limited amount of buffering (of already handled names) by both encoder and decoder; this is especially beneficial to avoid unnecessary overhead for cases where there are few back references (mostly or completely unique values)

Mechanism for resolving value string references differs from that used for key name references, so the two are explained separately below.

#### Shared value Strings

Support for shared value Strings is optional, in that generator can choose to either check for shareable value Strings or omit the checks.
Format header will indicate which option generator chose: if header is missing, default value of `false` (no checks done for shared value Strings; no back-references exist in encoded content) must be assumed.

One basic limitation is the encoded byte length of a String value that can be referenced is 64 bytes or less. Longer Strings can not be referenced. This is done as a performance optimization, as longer Strings are less likely to be shareable; and also because equality checks for longer Strings are most costly.
As a result, parser only should keep references for eligible Strings during parsing.

Reference length allowed by format is 10 bits, which means that encoder can replace references to most recent 1024 potentially shareable (referenceable) value Strings.

For both encoding (writing) and decoding (parsing), same basic tumbling-window algorithm is used: when a potentially eligible String value is to be written, generator can check whether it has already written such a String, and has retained reference. If so, reference value (between 0 and 1023 -- but with limits, see "Avoding References" below) can be written instead of String value.
If no such String has been written (as per generator's knowledge -- it is not required to even check this and lookup data structure may be lossy and only recognize some formerly written Strings), value is to be written.
If its encoded length indicates that it is indeeed shareable (which can not be known before writing, as check is based on byte length, not character length!), decoder is to add value into its shareable String buffer -- as long as buffer size does not exceed that of 1024 values. If it already has 1024 values, it MUST clear out buffer and start from first entry. This means that reference values are NOT relative back references, but rather offsets from beginning of reference buffer.

Similarly, parser has to keep track of decoded short (byte length <= 64 bytes) Strings seen so far, and have buffer of up to 1024 such values; clearing out buffer when it fills is done same way as during content generation.
Any shared string value references are resolved against this buffer.

Note: when a shared String is written or parsed, no entry is added to the shared value buffer (since one must already be in it)

#### Shared key name Strings

Support for shared property names is optional, in that generator can choose to either check for shareable property names or omit the checks.
Format header will indicate which option generator chose: if header is missing, default value of "trues" (checking done for shared property names is made, and encoded content MAY contain back-references to share names) must be assumed.

Shared key resolution is done same way as shared String value resolution, but buffers used are separate. Buffer sizes are same, 1024.

#### Avoiding references `0x??FE` and `0x??FF`

In order to avoid encoding bytes with values of `0xFE` and `0xFF` (similar to "Safe Binary Encoding"), a small number of otherwise referencable Value and Key Name Strings MUST NOT BE referenced by encoders.

Basically, of all possible values for long references -- `0x0040` - `0x03FF` -- ones where lower byte is `0xFE` or `0xFF` must be avoided by generator (high order byte can never conflict).
So, references like `0x00FE`, `0x00FF`, `0x1FE`, ... `0x3FF` MUST NOT BE used during encoding.
Generators can implement block in different ways but possible the simplest is to simply not store lookup entries for these indexes.

On decoder side decoder must keep track of these indexes (in the sense that non-shared Value/Key String has specific back reference index) but should not received any back references.
Decoder may (but do not have to) verify that no such references are found.
It may also choose to not keep track of such non-referencable Value/Key name Strings on decoding.

NOTE: while this requirement has been implemented by some Codecs (Jackson, in particular), it was not formally documented prior to specification version 1.0.6. It is considered a requirement of Smile v1 format encoders.

-----

## Future improvement ideas

**NOTE**': version 1.0 does **'NOT**' support any of features presented in this section; they are documented as ideas for future work.

### In-frame compression?

Although there were initial plans to allow in-frame (in-content) compression for individual values, it was decided that support would not be added for initial version, mostly since it was felt that compression of the whole document typically yields better results. For some use cases this may not be true, however; especially when semi-random access is desired.

Since enough type bits were left reserved for binary and long-text types, support may be added for future versions.

### Longer length-prefixed data?

Given that encoders may be able to determine byte-length for value strings longer than 64 bytes (current limit for "short" strings), it might make sense to add value types with 2-byte prefix (or maybe just 1-byte prefix and additional length information after first fixed 64 bytes, since that allows output at constant location. Performance measurements should be made to ensure that such an option would improve performance as that would be main expected benefit.

### Pre-defined shared values (back-refs)

For messages with little redundancy, but small set of always used names (from schema), it would be possible to do something similar to what deflate/gzip allows: defining "pre-amble", to allow back-references to pre-defined set of names and text values.
For example, it would be possible to specify 64 names and/or shared string values for both serializer and deserializer to allow back-references to this pre-defined set of names and/or string values. This would both improve performance and reduce size of content.

### Filler value(s)

It might make sense to allocate a "no-op" value or values to allow for padding of messages.
This would be useful for things like:

* Allow rounding up message size, for example to align entries in memory
* Leave slack for possible in-place additions or modifications (like always allocating fixed space for String values)

This would be a simple addition.

### Chunked values

(note: inspiration for this came from [CBOR](https://tools.ietf.org/html/rfc7049) format)

As an alternative for either requiring full content length (binary data), or end marker (long Strings, Objects, arrays),
and to specifically allow better buffering during encoding, it might make sense to allow "chunked" variants wherein
long content is encoded in chunks, size of which is individual indicated with length prefix, but whose total size
need not be calculated. This would work well for including large data incrementally, and it could also allow for
more efficient and flexible decoding.

-----

### Appendix A: External definitions

#### ZigZag encoding for VInts

Smile uses `ZigZag` encoding (defined for [protobuf format](http://code.google.com/apis/protocolbuffers/docs/encoding.html),
(see [StackOverflow question](http://stackoverflow.com/questions/2210923/zig-zag-decoding) for example)
which is a variant of generic [VInts](http://en.wikipedia.org/wiki/Variable-length_quantity) (Variable-length INTegers).

Encoding is done logically as a two-step process:

1. Use `ZigZag` encoding to convert signed values to unsigned values: essentially this will "move the sign bit" as the LSB.
2. Encode remaining bits of unsigned integral number, starting with the most significant bits: the last byte is indicated by setting the sign bit; all the other bytes have sign bit clear.
    * Last byte has only 6 data bits; second-highest bit MUST be clear (to ensure that value `0xFF` is never used for encoding; values `0xC0` - `0xFF` are not used for the last byte).
    * Other bytes have 7 data bits.
