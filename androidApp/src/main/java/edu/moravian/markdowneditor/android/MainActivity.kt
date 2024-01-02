package edu.moravian.markdowneditor.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import edu.moravian.markdowneditor.android.markdown.markdownMarkupTree
import edu.moravian.markdowneditor.android.render.MarkupText

/*
# h1 Heading 8-) &copy;
## h2 Heading ##
### h3 Heading
#### h4 Heading
##### h5 Heading
###### h6 Heading

h1 Heading - alt
================

h2 Heading - alt
----------------

## Horizontal Rules

___

---

***


## Typographic replacements

Enable typographer option to see result.

(c) (C) (r) (R) (tm) (TM) (p) (P) +-

test.. test... test..... test?..... test!....

!!!!!! ???? ,,  -- ---

Escaping: \\ \` \* \_ \{ \} \[ \] \< \> \( \) \# \+ \- \. \! \|

"Smartypants, double quotes" and 'single quotes'

  Paragraph indented.

Paragraph with
break in it.

Another breaking\
paragraph.

<s title="a">A long</s> paragraph with anchor in the middle of it. Rest of the text is garbage currentOnStart and
currentOnStop are not needed as DisposableEffect keys, because their value never change in
Composition due to the usage of rememberUpdatedState. If you don't pass lifecycleOwner as a
parameter and it changes, <b id="homescreen" title="From HomeScreen!">HomeScreen</b> recomposes, but
the <i id="disposableeffect" title="From DisposableEffect!">DisposableEffect</i> isn't disposed of
and restarted. That causes problems because the wrong lifecycleOwner is used from that
point <u title="z">onward</u>.


 */


val exampleText: String = """
## Comments

[//]: # (This may be the most platform independent comment)
[//]: <> (Another comment)

Do we support <!-- comments -->?

## Emphasis ##

**This is bold text**

__This is bold text__

*This is italic text*

_This is italic text_

~~Strikethrough~~


## Blockquotes

> Blockquotes can also be nested...
>> ...by using additional greater-than signs right next to each other...
> > > ...or with spaces between arrows.

> Blockquotes going
> > back
> and forth
> done


## Lists

Unordered

 + Create a list by starting a line with `+`, `-`, or `*`
 + Sub-lists are made by indenting 2 spaces:
  - Marker character change forces new list start:
    * Ac tristique libero volutpat at
    + Facilisis in pretium nisl aliquet
    - Nulla volutpat aliquam velit
 + Very easy!

Ordered

1. Lorem ipsum dolor sit amet
2. Consectetur adipiscing elit
3. Integer molestie lorem at massa


1. You can use sequential numbers...
1. ...or keep all the numbers as `1.`

Start numbering with offset:

57. foo
1. bar

## Code

Inline `code`

Inline ``code `with` backticks``

No code \` no code \`\`` `

Indented code

    // Some comments
    line 1 of code
    line 2 of code
    line 3 of code


Block code "fences"

```
Sample text here...
```

Syntax highlighting

``` js
var foo = function (bar) {
    // very long line of code that goes off the screen to see how it behaves
    return bar++;
};

console.log(foo(5));
```

~~~ js
using tildes
~~~

## Links

[link text](http://dev.nodeca.com)

[link with title](http://nodeca.github.io/pica/demo/ "title text!")

[link with single quote title](http://nodeca.github.io/pica/demo/ 'title text!')

Internal reference link to [h2 Heading](#h2-heading)

Internal reference link to [a point within a paragraph](#disposableeffect)

Basic link <https://github.com/nodeca/pica>

Email link <fake@example.com>

GFM autolinks: https://github.com/nodeca/pica  www.github.com/nodeca/pica

Reference links [hobbit-hole][1] and [b][2] and finally a shorty [hello]

[1]: <https://en.wikipedia.org/wiki/Hobbit#Lifestyle> "Hobbit lifestyles"
[2]: https://www.google.com 'Google'
[hello]: http://example.com


## Images

![Minion](https://octodex.github.com/images/minion.png)
![Stormtroopocat](https://octodex.github.com/images/stormtroopocat.jpg "The Stormtroopocat")

Like links, Images also have a footnote style syntax

With a reference later in the document defining the URL location:

![Alt text][id]

[id]: https://octodex.github.com/images/dojocat.jpg  "The Dojocat"


## HTML

Hello <b>HTML</b> <i><b>world</b></i> <br> <u>test</u> <span style="color: red">colored</span>.

Complex example of tag ordering <i><b>a</b></i><s><u>b</u></s>

<div>Block of html?<b><i>yes</i></b></div>

Text with  odd	spacing
and linebreaks

## Tables

| Option | Description |
| ------ | ----------- |
| data   | path to data files to supply the data that will be passed into templates. |
| engine | engine to be used for processing templates. Handlebars is the default. |
| ext    | extension to be used for dest files. |

Right and Center aligned columns

| Option | Description |
| ------:| :----------:|
| data   | path to data files to supply the data that will be passed into templates. |
| engine | engine to be used for processing templates. Handlebars is the default. |
| ext    | extension to be used for dest files. |

Missing Pipes

Opt | Description
------:| :----------:
data   | path to data files to supply the data that will be passed into templates.

##  ##

-  [ ] Item 1
- [ ] Item 2
-   [x] Checked item

## Testing

Start a paragraph to make inline<!-- comment --><!----><?php echo "test"; ?><![CDATA[character data]]>x

""".trimIndent()


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MarkupText(
                        markdownMarkupTree(exampleText),
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        println("URL clicked: $it")
                    }
                }
            }
        }
    }
}
