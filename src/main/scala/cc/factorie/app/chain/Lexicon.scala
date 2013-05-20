/* Copyright (C) 2008-2010 University of Massachusetts Amherst,
   Department of Computer Science.
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://code.google.com/p/factorie/
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package cc.factorie.app.chain
import cc.factorie._
import scala.collection.mutable.{ArrayBuffer,HashMap}

/** A list of words or phrases, with methods to easily check whether a Token (or more generally a cc.factorie.app.chain.Observation) is in the list.
    @author Andrew McCallum */
class Lexicon(val caseSensitive:Boolean) {
  import scala.io.Source
  import java.io.File
  /** Populate lexicon from file, with one entry per line, consisting of space-separated tokens. */
  def this(filename:String) = { this(false); this.++=(Source.fromFile(new File(filename))(scala.io.Codec.UTF8)) }
  var lexer = cc.factorie.app.strings.nonWhitespaceClassesSegmenter // TODO Make this a choice
  class LexiconToken(val string:String) extends Observation[LexiconToken] {
    var next: LexiconToken = null
    var prev: LexiconToken = null
    def hasNext = next != null
    def hasPrev = prev != null
    def position = lengthToEnd
    def lengthToEnd: Int = if (next == null) 1 else 1 + next.lengthToEnd
  }
  private def newLexiconTokens(words:Seq[String]): Seq[LexiconToken] = {
    val result = new ArrayBuffer[LexiconToken]
    var t: LexiconToken = null
    for (word <- words) {
      val t2 = new LexiconToken(word)
      t2.prev = t
      if (t != null) t.next = t2
      t = t2
      result += t2
    }
    result
  }
  val contents = new HashMap[String,List[LexiconToken]];
  private def _key(s:String) = if (caseSensitive) s else s.toLowerCase
  private def +=(t:LexiconToken): Unit = {
    val key = _key(t.string)
    val old: List[LexiconToken] = contents.getOrElse(key, Nil)
    contents(key) = t :: old
  }
  private def addAll(ts:Seq[LexiconToken]): Unit = {
    //println("Lexicon adding "+ts.map(_.word))
    ts.foreach(t => this += t)
  }
  /** Add a new lexicon entry consisting of a single string word */
  def +=(w:String): Unit = this.+=(new LexiconToken(w))
  /** Add a new lexicon entry consisting of a multi-string phrase. */
  def +=(ws:Seq[String]): Unit = this.addAll(newLexiconTokens(if (caseSensitive) ws else ws.map(_.toLowerCase)))
  def ++=(source:Source): Unit = for (line <- source.getLines()) { this.+=(lexer.regex.findAllIn(line).toSeq); /*println("TokenSeqs.Lexicon adding "+line)*/ }
  //def phrases: Seq[String] = contents.values.flatten.distinct
  /** Do any of the Lexicon entries contain the given word string. */
  def contains(s:String): Boolean = contents.contains(s)
  /** Is 'query' in the lexicon, accounting for lexicon phrases and the context of 'query' */
  def contains[T<:Observation[T]](query:T): Boolean = {
    //println("contains "+query.word+" "+query.hasPrev+" "+query)
    val key = _key(query.string)
    val entries = contents.getOrElse(key, Nil)
    for (entry <- entries) {
      var te = entry
      var tq = query
      var result = true
      // Go the beginning of this lexicon entry
      while (te.hasPrev && result) {
        if (!tq.hasPrev) return false
        te = te.prev; tq = tq.prev
      }
      //println("  Trying "+query.word+" "+entry.seq.map(_.word).toList)
      // Check for match all the way to the end of this lexicon entry
      do {
        if ((!caseSensitive && te.string != tq.string.toLowerCase) || (caseSensitive && te.string != tq.string)) result = false
        te = te.next; tq = tq.next
      } while (te != null && tq != null && result == true)   
      if (result && te == null) {
        //print(" contains length="+entry.length+"  "+entry.seq.map(_.word).toList)
        return true
      }
    }
    false
  }
  /** Is 'query' in the lexicon, ignoring context. */
  def containsSingle[T<:Observation[T]](query:T): Boolean = contents.contains(_key(query.string))
  /** Return length of match, or -1 if no match. */
  def startsAt[T<:Observation[T]](query:T): Int = {
    val key = _key(query.string)
    val entries = contents.getOrElse(key, Nil)
    for (entry <- entries.filter(_.hasPrev == false).sortBy(entry => -entry.lengthToEnd)) { // Sort so that we look for long entries first
      var te = entry
      var tq = query
      var len = 0
      var found = true
      // Query must be at the the beginning of this lexicon entry
      // Check for match all the way to the end of this lexicon entry
      do {
        if ((!caseSensitive && te.string != tq.string.toLowerCase) || (caseSensitive && te.string != tq.string)) found = false
        len += 1
        te = te.next; tq = tq.next
      } while (te != null && tq != null && found == true)   
      if (found && te == null) {
        //print(" contains length="+entry.length+"  "+entry.seq.map(_.word).toList)
        return len
      }
    }
    -1
  }
}

