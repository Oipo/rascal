package org.rascalmpl.repl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.rascalmpl.interpreter.asserts.Ambiguous;
import org.rascalmpl.interpreter.result.IRascalResult;
import org.rascalmpl.interpreter.utils.LimitedResultWriter.IOLimitReachedException;
import org.rascalmpl.interpreter.utils.ReadEvalPrintDialogMessages;
import org.rascalmpl.interpreter.utils.StringUtils;
import org.rascalmpl.interpreter.utils.StringUtils.OffsetLengthTerm;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.ValueFactoryFactory;
import org.rascalmpl.values.uptr.RascalValueFactory;
import org.rascalmpl.values.uptr.TreeAdapter;

import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.io.StandardTextWriter;
import io.usethesource.vallang.type.Type;

public abstract class BaseRascalREPL implements ILanguageProtocol {
    protected enum State {
        FRESH,
        CONTINUATION,
        DEBUG,
        DEBUG_CONTINUATION
    }

    private State currentState = State.FRESH;

    protected State getState() {
        return currentState;
    }

    private final static int LINE_LIMIT = 200;
    private final static int CHAR_LIMIT = LINE_LIMIT * 20;
    protected String currentPrompt = ReadEvalPrintDialogMessages.PROMPT;
    private StringBuffer currentCommand;
    private final StandardTextWriter indentedPrettyPrinter;
    private final boolean htmlOutput;
    private final static IValueFactory VF = ValueFactoryFactory.getValueFactory();

    public BaseRascalREPL(boolean prettyPrompt, boolean allowColors, boolean htmlOutput) throws IOException, URISyntaxException {
        this.htmlOutput = htmlOutput;
        
        if (allowColors) {
            indentedPrettyPrinter = new ReplTextWriter(true);
        }
        else {
            indentedPrettyPrinter = new StandardTextWriter(true);
        }
    }
    
    @Override
    public String getPrompt() {
        return currentPrompt;
    }

    @Override
    public void handleInput(String line, Map<String, String> output, Map<String, String> metadata)  throws InterruptedException {
        assert line != null;

        try {
            if (line.trim().length() == 0) {
                // cancel command
                getErrorWriter().println(ReadEvalPrintDialogMessages.CANCELLED);
                currentPrompt = ReadEvalPrintDialogMessages.PROMPT;
                currentCommand = null;
                currentState = State.FRESH;
                return;
            }
            if (currentCommand == null) {
                // we are still at a new command so let's see if the line is a full command
                if (isStatementComplete(line)) {
                    printResult(evalStatement(line, line), output, metadata);
                }
                else {
                    currentCommand = new StringBuffer(line);
                    currentPrompt = ReadEvalPrintDialogMessages.CONTINUE_PROMPT;
                    currentState = State.CONTINUATION;
                    return;
                }
            }
            else {
                currentCommand.append('\n');
                currentCommand.append(line);
                if (isStatementComplete(currentCommand.toString())) {
                    printResult(evalStatement(currentCommand.toString(), line), output, metadata);
                    currentPrompt = ReadEvalPrintDialogMessages.PROMPT;
                    currentCommand = null;
                    currentState = State.FRESH;
                    return;
                }
            }
        } catch (IOException ie) {
            throw new RuntimeException(ie);
        } catch (Ambiguous e) {
            getErrorWriter().println("Internal error: ambiguous command: " + TreeAdapter.yield(e.getTree()));
            return;
        }
        
    }

    @Override
    public void handleReset(Map<String,String> output, Map<String,String> metadata) throws InterruptedException {
        handleInput("", output, metadata);
    }
    
    protected void printResult(IRascalResult result, Map<String,String> output, Map<String, String> metadata) throws IOException {
        if (result == null || result.getValue() == null) {
            return;
        }
       
        StringWriter out = new StringWriter();
        
        if (htmlOutput) {
            writeRascalToHTML(result, out);    
        }
        else {
            writeOutputToString(result, out);
        }
        
        if (!out.toString().equals("")) {
            output.put("text/" + (htmlOutput ? "html" : "plain"), out.toString());
        }
    }            

    private void writeOutputToString(IRascalResult result, StringWriter out) throws IOException {
        IValue value = result.getValue();
        Type type = result.getType();

        if (type.isAbstractData() && type.isStrictSubtypeOf(RascalValueFactory.Tree) && !type.isBottom()) {
            out.write(type.toString());
            out.write(": ");
            // we unparse the tree
            out.write("(" + type.toString() +") `");
            TreeAdapter.yield((IConstructor)result.getValue(), true, out);
            out.write("`");
        }
        else {
            out.write(type.toString());
            out.write(": ");
               
            // limit both the lines and the characters
            try (Writer wrt = new LimitedWriter(new LimitedLineWriter(out, LINE_LIMIT), CHAR_LIMIT)) {
                indentedPrettyPrinter.write(value, wrt);
            }
            catch (IOLimitReachedException e) {
                // ignore since this is what we wanted
            }
        }
        
        out.write("\n");
    }
    
    public void writeRascalToHTML(IRascalResult result, StringWriter out) throws IOException {
        Type type = result.getType();
        
        if (type.isAbstractData() && type.isStrictSubtypeOf(RascalValueFactory.Tree) && !type.isBottom()) {
            out.write("<pre title=\"" + type.toString() + "\">");
            out.write("`");
            TreeAdapter.yield((IConstructor)result.getValue(), true, out);
            out.write("`");
            out.write("</pre>");
        }
        else {
            out.write("<pre title=\"" + type.toString() + "\">");
            
            try (Writer wrt = new LimitedWriter(new LimitedLineWriter(out, LINE_LIMIT), CHAR_LIMIT)) {
                indentedPrettyPrinter.write(result.getValue(), wrt);
            }
            catch (IOLimitReachedException e) {
                // ignore since this is what we wanted
            }
            out.write("</pre>");
        }
    }

    protected abstract PrintWriter getErrorWriter();
    protected abstract PrintWriter getOutputWriter();

    public abstract IRascalResult evalStatement(String statement, String lastLine) throws InterruptedException;

    /**
     * provide which :set flags  (:set profiling true for example)
     * @return strings that can be set
     */
    protected abstract SortedSet<String> getCommandLineOptions();
    protected abstract Collection<String> completePartialIdentifier(String line, int cursor, String qualifier, String identifier);
    protected abstract Collection<String> completeModule(String qualifier, String partialModuleName);
    
    protected boolean isREPLCommand(String line){
    	return line.startsWith(":");
    }

    @Override
    public CompletionResult completeFragment(String line, int cursor) {
        if (currentState == State.FRESH) {
            String trimmedLine = line.trim();
            if (isREPLCommand(trimmedLine)) {
                return completeREPLCommand(line, cursor);
            }
            if (trimmedLine.startsWith("import ") || trimmedLine.startsWith("extend ")) {
                return completeModule(line, cursor);
            }
        }
        int locationStart = StringUtils.findRascalLocationStart(line, cursor);

        if (locationStart != -1) {
            return completeLocation(line, locationStart);
        }
        return completeIdentifier(line, cursor);
    }

    protected CompletionResult completeIdentifier(String line, int cursor) {
        OffsetLengthTerm identifier = StringUtils.findRascalIdentifierAtOffset(line, cursor);
        if (identifier != null) {
            String[] qualified = StringUtils.splitQualifiedName(unescapeKeywords(identifier.term));
            String qualifier = qualified.length == 2 ? qualified[0] : "";
            String qualifee = qualified.length == 2 ? qualified[1] : qualified[0];
            Collection<String> suggestions = completePartialIdentifier(line, cursor, qualifier, qualifee);
            if (suggestions != null && ! suggestions.isEmpty()) {
                return new CompletionResult(identifier.offset, escapeKeywords(suggestions));
            }
        }
        return null;
    }


    private static final Pattern splitIdentifiers = Pattern.compile("[:][:]");
    private static Collection<String> escapeKeywords(Collection<String> suggestions) {
        return suggestions.stream()
                        .map(s -> splitIdentifiers.splitAsStream(s + " ") // add space such that the ending "::" is not lost
                                        .map(BaseRascalREPL::escapeKeyword)
                                        .collect(Collectors.joining("::")).trim()
                                        )
                        .collect(Collectors.toList());
    }
    private static String unescapeKeywords(String term) {
        return splitIdentifiers.splitAsStream(term + " ") // add space such that the ending "::" is not lost
                        .map(BaseRascalREPL::unescapeKeyword)
                        .collect(Collectors.joining("::")).trim()
                        ;
    }

    private static final Set<String> RASCAL_KEYWORDS =  new HashSet<String>();

    private static void assureKeywordsAreScrapped() {
        if (RASCAL_KEYWORDS.isEmpty()) {
            synchronized (RASCAL_KEYWORDS) {
                if (!RASCAL_KEYWORDS.isEmpty()) {
                    return;
                }

                String rascalGrammar = "";
                try (Reader grammarReader = URIResolverRegistry.getInstance().getCharacterReader(ValueFactoryFactory.getValueFactory().sourceLocation("std", "", "/lang/rascal/syntax/Rascal.rsc"))) {
                    StringBuilder res = new StringBuilder();
                    char[] chunk = new char[8 * 1024];
                    int read;
                    while ((read = grammarReader.read(chunk, 0, chunk.length)) != -1) {
                        res.append(chunk, 0, read);
                    }
                    rascalGrammar = res.toString();
                }
                catch (IOException | URISyntaxException e) {
                    e.printStackTrace();
                }
                if (!rascalGrammar.isEmpty()) {
                    /*
                     * keyword RascalKeywords
                     * = "o"
                     * | "syntax"
                     * | "keyword"
                     * | "lexical"
                     * ...
                     * ;
                     */
                    Pattern findKeywordSection = Pattern.compile("^\\s*keyword([^=]|\\s)*=(?<keywords>([^;]|\\s)*);", Pattern.MULTILINE);
                    Matcher m = findKeywordSection.matcher(rascalGrammar);
                    if (m.find()) {
                        String keywords = "|" + m.group("keywords");
                        Pattern keywordEntry = Pattern.compile("\\s*[|]\\s*[\"](?<keyword>[^\"]*)[\"]");
                        m = keywordEntry.matcher(keywords);
                        while (m.find()) {
                            RASCAL_KEYWORDS.add(m.group("keyword"));
                        }
                    }
                    /*
                     * syntax BasicType
                    = \value: "value" 
                    | \loc: "loc" 
                    | \node: "node" 
                     */
                    Pattern findBasicTypeSection = Pattern.compile("^\\s*syntax\\s*BasicType([^=]|\\s)*=(?<keywords>([^;]|\\s)*);", Pattern.MULTILINE);
                    m = findBasicTypeSection.matcher(rascalGrammar);
                    if (m.find()) {
                        String keywords = "|" + m.group("keywords");
                        Pattern keywordEntry = Pattern.compile("\\s*[|][^:]*:\\s*[\"](?<keyword>[^\"]*)[\"]");
                        m = keywordEntry.matcher(keywords);
                        while (m.find()) {
                            RASCAL_KEYWORDS.add(m.group("keyword"));
                        }
                    }
                }
                if (RASCAL_KEYWORDS.isEmpty()) {
                    RASCAL_KEYWORDS.add("syntax");
                }
            }
        }
    }

    private static String escapeKeyword(String s) {
        assureKeywordsAreScrapped();
        if (RASCAL_KEYWORDS.contains(s)) {
            return "\\" + s;
        }
        return s;
    }
    private static String unescapeKeyword(String s) {
        assureKeywordsAreScrapped();
        if (s.startsWith("\\") && !s.contains("-")) {
            return s.substring(1);
        }
        return s;
    }


    @Override
    public boolean supportsCompletion() {
        return true;
    }

    @Override
    public boolean printSpaceAfterFullCompletion() {
        return false;
    }

    private CompletionResult completeLocation(String line, int locationStart) {
        int locationEnd = StringUtils.findRascalLocationEnd(line, locationStart);
        try {
            String locCandidate = line.substring(locationStart + 1, locationEnd + 1);
            if (!locCandidate.contains("://")) {
                return null;
            }
            ISourceLocation directory = VF.sourceLocation(new URI(locCandidate));
            String fileName = "";
            URIResolverRegistry reg = URIResolverRegistry.getInstance();
            if (!reg.isDirectory(directory)) {
                // split filename and directory
                String fullPath = directory.getPath();
                int lastSeparator = fullPath.lastIndexOf('/');
                fileName = fullPath.substring(lastSeparator +  1);
                fullPath = fullPath.substring(0, lastSeparator);
                directory = VF.sourceLocation(directory.getScheme(), directory.getAuthority(), fullPath);
                if (!reg.isDirectory(directory)) {
                    return null;
                }
            }
            String[] filesInPath = reg.listEntries(directory);
            URI directoryURI = directory.getURI();
            Set<String> result = new TreeSet<>(); // sort it up
            for (String currentFile : filesInPath) {
                if (currentFile.startsWith(fileName)) {
                    URI currentDir = URIUtil.getChildURI(directoryURI, currentFile);
                    boolean isDirectory = reg.isDirectory(VF.sourceLocation(currentDir));
                    result.add(currentDir.toString() + (isDirectory ? "/" : "|"));
                }
            }
            if (result.size() > 0) {
                return new CompletionResult(locationStart + 1, result);
            }
            return null;
        }
        catch (URISyntaxException|IOException e) {
            return null;
        }
    }

    public CompletionResult completeModule(String line, int cursor) {
        OffsetLengthTerm identifier = StringUtils.findRascalIdentifierAtOffset(line, line.length());
        if (identifier != null) {
            String[] qualified = StringUtils.splitQualifiedName(unescapeKeywords(identifier.term));
            String qualifier = qualified.length == 2 ? qualified[0] : "";
            String qualifee = qualified.length == 2 ? qualified[1] : qualified[0];
            Collection<String> suggestions = completeModule(qualifier, qualifee);
            if (suggestions != null && ! suggestions.isEmpty()) {
                return new CompletionResult(identifier.offset, escapeKeywords(suggestions));
            }
        }
        return null;
    }

    protected CompletionResult completeREPLCommand(String line, int cursor) {
        return RascalCommandCompletion.complete(line, cursor, getCommandLineOptions(), (l,i) -> completeIdentifier(l,i), (l,i) -> completeModule(l,i));
    }
}
