////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2021 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.puppycrawl.tools.checkstyle.AbstractPathTestSupport.addEndOfLine;
import static com.puppycrawl.tools.checkstyle.internal.utils.TestUtil.isUtilsClassHasPrivateConstructor;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.Assertion;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.powermock.reflect.Whitebox;

import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.AutomaticBean;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Violation;
import com.puppycrawl.tools.checkstyle.internal.testmodules.TestRootModuleChecker;
import com.puppycrawl.tools.checkstyle.internal.utils.TestUtil;
import com.puppycrawl.tools.checkstyle.jre6.charset.StandardCharsets;
import com.puppycrawl.tools.checkstyle.jre6.file.Files7;
import com.puppycrawl.tools.checkstyle.jre6.file.Path;
import com.puppycrawl.tools.checkstyle.jre6.file.Paths;
import com.puppycrawl.tools.checkstyle.jre6.lang.System7;
import com.puppycrawl.tools.checkstyle.utils.ChainedPropertyUtil;

public class MainTest {

    private static final String SHORT_USAGE = String.format(Locale.ROOT,
            "Usage: checkstyle [OPTIONS]... FILES...%n"
            + "Try 'checkstyle --help' for more information.%n");

    private static final String USAGE = String.format(Locale.ROOT,
          "Usage: checkstyle [-dEghjJtTV] [-b=<xpath>] [-c=<configurationFile>] "
                  + "[-f=<format>]%n"
                  + "                  [-o=<outputPath>] [-p=<propertiesFile>] "
                  + "[-s=<suppressionLineColumnNumber>]%n"
                  + "                  [-w=<tabWidth>] [-e=<exclude>]... [-x=<excludeRegex>]... "
                  + "<files>...%n"
                  + "Checkstyle verifies that the specified source code files adhere to the"
                  + " specified rules. By default%n"
                  + "violations are reported to standard out in plain format. Checkstyle requires"
                  + " a configuration XML%n"
                  + "file that configures the checks to apply.%n"
                  + "      <files>...            One or more source files to verify%n"
                  + "  -b, --branch-matching-xpath=<xpath>%n"
                  + "                            Shows Abstract Syntax Tree(AST) branches that"
                  + " match given XPath query.%n"
                  + "  -c=<configurationFile>    Specifies the location of the file that defines"
                  + " the configuration%n"
                  + "                              modules. The location can either be a"
                  + " filesystem location, or a name%n"
                  + "                              passed to the ClassLoader.getResource()"
                  + " method.%n"
                  + "  -d, --debug               Prints all debug logging of CheckStyle utility.%n"
                  + "  -e, --exclude=<exclude>   Directory/file to exclude from CheckStyle. The"
                  + " path can be the full,%n"
                  + "                              absolute path, or relative to the current"
                  + " path. Multiple excludes are%n"
                  + "                              allowed.%n"
                  + "  -E, --executeIgnoredModules%n"
                  + "                            Allows ignored modules to be run.%n"
                  + "  -f=<format>               Specifies the output format. Valid values: "
                  + "xml, sarif, plain for%n"
                  + "                              XMLLogger, SarifLogger, and "
                  + "DefaultLogger respectively. Defaults to%n"
                  + "                              plain.%n"
                  + "  -g, --generate-xpath-suppression%n"
                  + "                            Generates to output a suppression xml to use"
                  + " to suppress all violations%n"
                  + "                              from user's config. Instead of printing every"
                  + " violation, all%n"
                  + "                              violations will be catched and single"
                  + " suppressions xml file will be%n"
                  + "                              printed out. Used only with -c option. Output"
                  + " location can be%n"
                  + "                              specified with -o option.%n"
                  + "  -h, --help                Show this help message and exit.%n"
                  + "  -j, --javadocTree         Prints Parse Tree of the Javadoc comment. The"
                  + " file have to contain only%n"
                  + "                              Javadoc comment content without including"
                  + " '/**' and '*/' at the%n"
                  + "                              beginning and at the end respectively. The"
                  + " option cannot be used%n"
                  + "                              other options and requires exactly one file"
                  + " to run on to be specified.%n"
                  + "  -J, --treeWithJavadoc     Prints Abstract Syntax Tree(AST) with Javadoc"
                  + " nodes and comment nodes%n"
                  + "                              of the checked file. Attention that line number"
                  + " and columns will not%n"
                  + "                              be the same as it is a file due to the fact"
                  + " that each javadoc comment%n"
                  + "                              is parsed separately from java file. The"
                  + " option cannot be used with%n"
                  + "                              other options and requires exactly one file to"
                  + " run on to be specified.%n"
                  + "  -o=<outputPath>           Sets the output file. Defaults to stdout.%n"
                  + "  -p=<propertiesFile>       Sets the property files to load.%n"
                  + "  -s=<suppressionLineColumnNumber>%n"
                  + "                            Prints xpath suppressions at the file's line and"
                  + " column position.%n"
                  + "                              Argument is the line and column number"
                  + " (separated by a : ) in the%n"
                  + "                              file that the suppression should be generated"
                  + " for. The option cannot%n"
                  + "                              be used with other options and requires exactly"
                  + " one file to run on to%n"
                  + "                              be specified. ATTENTION: generated result will"
                  + " have few queries,%n"
                  + "                              joined by pipe(|). Together they will match all"
                  + " AST nodes on%n"
                  + "                              specified line and column. You need to choose"
                  + " only one and recheck%n"
                  + "                              that it works. Usage of all of them is also ok,"
                  + " but might result in%n"
                  + "                              undesirable matching and suppress other"
                  + " issues.%n"
                  + "  -t, --tree                Prints Abstract Syntax Tree(AST) of the checked"
                  + " file. The option cannot%n"
                  + "                              be used other options and requires exactly one"
                  + " file to run on to be%n"
                  + "                              specified.%n"
                  + "  -T, --treeWithComments    Prints Abstract Syntax Tree(AST) with comment"
                  + " nodes of the checked%n"
                  + "                              file. The option cannot be used with other"
                  + " options and requires%n"
                  + "                              exactly one file to run on to be specified.%n"
                  + "  -V, --version             Print version information and exit.%n"
                  + "  -w, --tabWidth=<tabWidth> Sets the length of the tab character. Used only"
                  + " with -s option. Default%n"
                  + "                              value is 8.%n"
                  + "  -x, --exclude-regexp=<excludeRegex>%n"
                  + "                            Directory/file pattern to exclude from CheckStyle."
                  + " Multiple excludes%n"
                  + "                              are allowed.%n");

    private static final Logger LOG = Logger.getLogger(MainTest.class.getName()).getParent();
    private static final Handler[] HANDLERS = LOG.getHandlers();
    private static final Level ORIGINAL_LOG_LEVEL = LOG.getLevel();

    private static final String EOL = System7.lineSeparator();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();
    @Rule
    public final SystemErrRule systemErr = new SystemErrRule().enableLog().mute();
    @Rule
    public final SystemOutRule systemOut = new SystemOutRule().enableLog().mute();

    private final Violation auditStartMessage = new Violation(1,
            Definitions.CHECKSTYLE_BUNDLE, "DefaultLogger.auditStarted", null, null,
            getClass(), null);

    private final Violation auditFinishMessage = new Violation(1,
            Definitions.CHECKSTYLE_BUNDLE, "DefaultLogger.auditFinished", null, null,
            getClass(), null);

    private final String noViolationsOutput = auditStartMessage.getViolation() + EOL
                    + auditFinishMessage.getViolation() + EOL;

    private static String getPath(String filename) {
        return "src/test/resources/com/puppycrawl/tools/checkstyle/main/" + filename;
    }

    private static String getNonCompilablePath(String filename) {
        return "src/test/resources-noncompilable/com/puppycrawl/tools/checkstyle/main/" + filename;
    }

    private static String getFilePath(String filename) throws IOException {
        return new File(getPath(filename)).getCanonicalPath();
    }

    /**
     * Restore original logging level and HANDLERS to prevent bleeding into other tests.
     */
    @Before
    public void setUp() {
        LOG.setLevel(ORIGINAL_LOG_LEVEL);

        for (Handler handler : LOG.getHandlers()) {
            boolean found = false;

            for (Handler savedHandler : HANDLERS) {
                if (handler == savedHandler) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                LOG.removeHandler(handler);
            }
        }
    }

    @Test
    public void testIsProperUtilsClass() throws Exception {
        assertTrue(
                isUtilsClassHasPrivateConstructor(Main.class, false), "Constructor is not private");
    }

    @Test
    public void testVersionPrint()
            throws Exception {
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Checkstyle version: null" + System7.lineSeparator(),
                        systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-V");
    }

    @Test
    public void testUsageHelpPrint()
            throws Exception {
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(USAGE,
                        systemOut.getLog(),
                        "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-h");
    }

    @Test
    public void testWrongArgument()
            throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                final String usage = "Unknown option: '-q'" + EOL
                        + SHORT_USAGE;
                assertEquals("", systemOut.getLog(), "Unexpected output log");
                assertEquals(usage, systemErr.getLog(), "Unexpected system error log");
            }
        });
        // need to specify a file:
        // <files> is defined as a required positional param;
        // picocli verifies required parameters before checking unknown options
        Main.main("-q", "file");
    }

    @Test
    public void testWrongArgumentMissingFiles()
            throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                // files is defined as a required positional param;
                // picocli verifies required parameters before checking unknown options
                final String usage = "Missing required parameter: '<files>'" + EOL + SHORT_USAGE;
                assertEquals("", systemOut.getLog(), "Unexpected output log");
                assertEquals(usage, systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-q");
    }

    @Test
    public void testNoConfigSpecified() throws IOException {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Must specify a config XML file." + System7.lineSeparator(),
                        systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main(getPath("InputMain.java"));
    }

    @Test
    public void testNonExistentTargetFile()
            throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Files to process must be specified, found 0."
                    + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", "/google_checks.xml", "NonExistentFile.java");
    }

    @Test
    public void testExistingTargetFileButWithoutReadAccess() throws Exception {
        final File file = temporaryFolder.newFile("testExistingTargetFileButWithoutReadAccess");
        final boolean isSuccessfulChange = file.setReadable(false);
        // skip execution if file is still readable, it is possible on some windows machines
        // see https://github.com/checkstyle/checkstyle/issues/7032 for details
        if (isSuccessfulChange) {
            exit.expectSystemExitWithStatus(-1);
            exit.checkAssertionAfterwards(new Assertion() {
                @Override
                public void checkAssertion() {
                    assertEquals("Unexpected output log", "Files to process must be specified, found 0."
                        + System7.lineSeparator(), systemOut.getLog());
                    assertEquals("Unexpected system error log", "", systemErr.getLog());
                }
            });
            Main.main("-c", "/google_checks.xml", file.getCanonicalPath());
        }
    }

    @Test
    public void testNonExistentConfigFile()
            throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Could not find config XML file "
                            + "'src/main/resources/non_existent_config.xml'." + EOL,
                        systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", "src/main/resources/non_existent_config.xml",
                getPath("InputMain.java"));
    }

    @Test
    public void testNonExistentOutputFormat() throws IOException {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("", systemOut.getLog(), "Unexpected output log");
                assertEquals("Invalid value for option '-f': expected one of [XML, SARIF, PLAIN]"
                            + " (case-insensitive) but was 'xmlp'" + EOL + SHORT_USAGE,
                        systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", "/google_checks.xml", "-f", "xmlp", getPath("InputMain.java"));
    }

    @Test
    public void testNonExistentClass() throws IOException {
        exit.expectSystemExitWithStatus(-2);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                final String cause = "com.puppycrawl.tools.checkstyle.api.CheckstyleException:"
                        + " cannot initialize module TreeWalker - ";
                assertTrue(systemErr.getLog().startsWith(cause), "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-non-existent-classname.xml"),
                getPath("InputMain.java"));
    }

    @Test
    public void testExistingTargetFile()
            throws IOException {
        Main.main("-c", getPath("InputMainConfig-classname.xml"), getPath("InputMain.java"));
        assertEquals(addEndOfLine(auditStartMessage.getViolation(),
                auditFinishMessage.getViolation()),
                systemOut.getLog(), "Unexpected output log");
        assertEquals("", systemErr.getLog(), "Unexpected system error log");
    }

    @Test
    public void testExistingTargetFileXmlOutput() throws IOException {
        Main.main("-c", getPath("InputMainConfig-classname.xml"), "-f", "xml",
                getPath("InputMain.java"));
        final String expectedPath = getFilePath("InputMain.java");
        final String version = Main.class.getPackage().getImplementationVersion();
        assertEquals(addEndOfLine(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<checkstyle version=\"" + version + "\">",
                "<file name=\"" + expectedPath + "\">",
                "</file>",
                "</checkstyle>"), systemOut.getLog(), "Unexpected output log");
        assertEquals("", systemErr.getLog(), "Unexpected system error log");
    }

    /**
     * This test method is created only to cover
     * pitest mutation survival at Main#getOutputStreamOptions.
     * No ability to test it by out general tests because
     * Main does not produce any output to System.out after report is generated,
     * System.out and System.err should be non-closed streams
     *
     * @throws Exception if there is an error.
     * @noinspection UseOfSystemOutOrSystemErr
     */
    @Test
    public void testNonClosedSystemStreams() throws Exception {
        Main.main("-c", getPath("InputMainConfig-classname.xml"),
                "-f", "xml",
                getPath("InputMain.java"));

        final Boolean closedOut = (Boolean) TestUtil
                .getClassDeclaredField(System.out.getClass(), "closing").get(System.out);
        assertThat("System.out stream should not be closed", closedOut, is(false));
        final Boolean closedErr = (Boolean) TestUtil
                .getClassDeclaredField(System.err.getClass(), "closing").get(System.err);
        assertThat("System.err stream should not be closed", closedErr, is(false));
    }

    /**
     * This test method is created only to cover
     * pitest mutation survival at Main#getOutputStreamOptions.
     * No ability to test it by out general tests.
     * It is hard test that inner stream is closed, so pure UT is created to validate result
     * of private method Main.getOutputStreamOptions
     *
     * @throws Exception if there is an error.
     */
    @Test
    public void testGetOutputStreamOptionsMethod() throws Exception {
        final Path path = new Path(new File(getPath("InputMain.java")));
        final AutomaticBean.OutputStreamOptions option =
                (AutomaticBean.OutputStreamOptions) TestUtil
                    .getClassDeclaredMethod(Main.class, "getOutputStreamOptions")
                    .invoke(null, path);
        assertThat("Main.getOutputStreamOptions return CLOSE on not null Path",
                option, is(AutomaticBean.OutputStreamOptions.CLOSE));
    }

    @Test
    public void testExistingTargetFilePlainOutput() throws Exception {
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(auditStartMessage.getViolation() + EOL
                        + auditFinishMessage.getViolation() + EOL, systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-classname.xml"),
                "-f", "plain",
                getPath("InputMain.java"));
        assertEquals(addEndOfLine(auditStartMessage.getViolation(),
                auditFinishMessage.getViolation()),
                systemOut.getLog(), "Unexpected output log");
        assertEquals("", systemErr.getLog(), "Unexpected system error log");
    }

    @Test
    public void testExistingTargetFileWithViolations() throws IOException {
        Main.main("-c", getPath("InputMainConfig-classname2.xml"), getPath("InputMain.java"));
        final Violation invalidPatternMessageMain = new Violation(1,
                "com.puppycrawl.tools.checkstyle.checks.naming.messages",
                "name.invalidPattern", new String[] {"InputMain", "^[a-z0-9]*$"},
                null, getClass(), null);
        final Violation invalidPatternMessageMainInner = new Violation(1,
                "com.puppycrawl.tools.checkstyle.checks.naming.messages",
                "name.invalidPattern", new String[] {"InputMainInner", "^[a-z0-9]*$"},
                null, getClass(), null);
        final String expectedPath = getFilePath("InputMain.java");
        assertEquals(
                addEndOfLine(auditStartMessage.getViolation(),
                    "[WARN] " + expectedPath + ":3:14: "
                        + invalidPatternMessageMain.getViolation()
                        + " [TypeName]",
                    "[WARN] " + expectedPath + ":5:7: "
                        + invalidPatternMessageMainInner.getViolation()
                        + " [TypeName]",
                    auditFinishMessage.getViolation()),
                systemOut.getLog(), "Unexpected output log");
        assertEquals("", systemErr.getLog(), "Unexpected system error log");
    }

    @Test
    public void testViolationsByGoogleAndXpathSuppressions() throws Exception {
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() throws IOException {
                assertThat("Unexpected output log", systemOut.getLog(), is(noViolationsOutput));
                assertThat("Unexpected system error log", systemErr.getLog(), is(""));
            }
        });
        System.setProperty("org.checkstyle.google.suppressionxpathfilter.config",
                getPath("InputMainViolationsForGoogleXpathSuppressions.xml"));
        Main.main("-c", "/google_checks.xml",
                getPath("InputMainViolationsForGoogle.java"));
    }

    @Test
    public void testViolationsByGoogleAndSuppressions() throws Exception {
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() throws IOException {
                assertThat("Unexpected output log", systemOut.getLog(), is(noViolationsOutput));
                assertThat("Unexpected system error log", systemErr.getLog(), is(""));
            }
        });
        System.setProperty("org.checkstyle.google.suppressionfilter.config",
                getPath("InputMainViolationsForGoogleSuppressions.xml"));
        Main.main("-c", "/google_checks.xml",
                getPath("InputMainViolationsForGoogle.java"));
    }

    @Test
    public void testExistingTargetFileWithError() throws Exception {
        exit.expectSystemExitWithStatus(2);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() throws IOException {
                final Violation errorCounterTwoMessage = new Violation(1,
                        Definitions.CHECKSTYLE_BUNDLE, Main.ERROR_COUNTER,
                        new String[] {String.valueOf(2)}, null, getClass(), null);
                final Violation invalidPatternMessageMain = new Violation(1,
                        "com.puppycrawl.tools.checkstyle.checks.naming.messages",
                        "name.invalidPattern", new String[] {"InputMain", "^[a-z0-9]*$"},
                        null, getClass(), null);
                final Violation invalidPatternMessageMainInner = new Violation(1,
                        "com.puppycrawl.tools.checkstyle.checks.naming.messages",
                        "name.invalidPattern", new String[] {"InputMainInner", "^[a-z0-9]*$"},
                        null, getClass(), null);
                final String expectedPath = getFilePath("InputMain.java");
                assertEquals(
                        addEndOfLine(auditStartMessage.getViolation(),
                            "[ERROR] " + expectedPath + ":3:14: "
                                + invalidPatternMessageMain.getViolation() + " [TypeName]",
                            "[ERROR] " + expectedPath + ":5:7: "
                                + invalidPatternMessageMainInner.getViolation() + " [TypeName]",
                            auditFinishMessage.getViolation()),
                        systemOut.getLog(), "Unexpected output log");
                assertEquals(addEndOfLine(errorCounterTwoMessage.getViolation()),
                        systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-classname2-error.xml"),
                getPath("InputMain.java"));
    }

    /**
     * Similar test to {@link #testExistingTargetFileWithError}, but for PIT mutation tests:
     * this test fails if the boundary condition is changed from {@code if (exitStatus > 0)}
     * to {@code if (exitStatus > 1)}.
     *
     * @throws Exception should not throw anything
     */
    @Test
    public void testExistingTargetFileWithOneError() throws Exception {
        exit.expectSystemExitWithStatus(1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() throws IOException {
                final Violation errorCounterTwoMessage = new Violation(1,
                        Definitions.CHECKSTYLE_BUNDLE, Main.ERROR_COUNTER,
                        new String[] {String.valueOf(1)}, null, getClass(), null);
                final Violation invalidPatternMessageMain = new Violation(1,
                        "com.puppycrawl.tools.checkstyle.checks.naming.messages",
                        "name.invalidPattern", new String[] {"InputMain1", "^[a-z0-9]*$"},
                        null, getClass(), null);
                final String expectedPath = getFilePath("InputMain1.java");
                assertEquals(
                        addEndOfLine(auditStartMessage.getViolation(),
                            "[ERROR] " + expectedPath + ":3:14: "
                                + invalidPatternMessageMain.getViolation() + " [TypeName]",
                            auditFinishMessage.getViolation()),
                        systemOut.getLog(), "Unexpected output log");
                assertEquals(addEndOfLine(errorCounterTwoMessage.getViolation()),
                        systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-classname2-error.xml"),
                getPath("InputMain1.java"));
    }

    @Test
    public void testExistingTargetFileWithOneErrorAgainstSunCheck() throws Exception {
        exit.expectSystemExitWithStatus(1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() throws IOException {
                final Violation errorCounterTwoMessage = new Violation(1,
                        Definitions.CHECKSTYLE_BUNDLE, Main.ERROR_COUNTER,
                        new String[] {String.valueOf(1)}, null, getClass(), null);
                final Violation message = new Violation(1,
                        "com.puppycrawl.tools.checkstyle.checks.javadoc.messages",
                        "javadoc.packageInfo", new String[] {},
                        null, getClass(), null);
                final String expectedPath = getFilePath("InputMain1.java");
                assertEquals(addEndOfLine(auditStartMessage.getViolation(),
                        "[ERROR] " + expectedPath + ":1: " + message.getViolation() + " [JavadocPackage]",
                        auditFinishMessage.getViolation()),
                        systemOut.getLog(), "Unexpected output log");
                assertEquals(addEndOfLine(errorCounterTwoMessage.getViolation()),
                        systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", "/sun_checks.xml", getPath("InputMain1.java"));
    }

    @Test
    public void testExistentTargetFilePlainOutputToNonExistentFile()
            throws Exception {
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("", systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-classname.xml"),
                "-f", "plain",
                "-o", temporaryFolder.getRoot() + "/output.txt",
                getPath("InputMain.java"));
    }

    @Test
    public void testExistingTargetFilePlainOutputToFile()
            throws Exception {
        final File file = temporaryFolder.newFile("file.output");
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("", systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-classname.xml"),
                "-f", "plain",
                "-o", file.getCanonicalPath(),
                getPath("InputMain.java"));
    }

    @Test
    public void testCreateNonExistentOutputFile() throws Exception {
        final String outputFile = temporaryFolder.getRoot().getCanonicalPath() + "nonexistent.out";
        assertFalse(new File(outputFile).exists(), "File must not exist");
        Main.main("-c", getPath("InputMainConfig-classname.xml"),
                "-f", "plain",
                "-o", outputFile,
                getPath("InputMain.java"));
        assertTrue(new File(outputFile).exists(), "File must exist");
    }

    @Test
    public void testExistingTargetFilePlainOutputProperties() throws Exception {
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(auditStartMessage.getViolation() + EOL
                        + auditFinishMessage.getViolation() + EOL, systemOut.getLog(),
                        "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-classname-prop.xml"),
                "-p", getPath("InputMainMycheckstyle.properties"), getPath("InputMain.java"));
        assertEquals(addEndOfLine(auditStartMessage.getViolation(),
                auditFinishMessage.getViolation()),
                systemOut.getLog(), "Unexpected output log");
        assertEquals("", systemErr.getLog(), "Unexpected system error log");
    }

    @Test
    public void testPropertyFileWithPropertyChaining() throws IOException {
        Main.main("-c", getPath("InputMainConfig-classname-prop.xml"),
            "-p", getPath("InputMainPropertyChaining.properties"), getPath("InputMain.java"));

        assertWithMessage("Unexpected output log")
            .that(systemOut.getLog())
            .isEqualTo(addEndOfLine(auditStartMessage.getViolation(),
                auditFinishMessage.getViolation()));
        assertWithMessage("Unexpected system error log")
            .that(systemErr.getLog())
            .isEqualTo("");
    }

    @Test
    public void testPropertyFileWithPropertyChainingUndefinedProperty() throws IOException {
        exit.expectSystemExitWithStatus(-2);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertWithMessage("Invalid error message")
                    .that(systemErr.getLog())
                    .contains(ChainedPropertyUtil.UNDEFINED_PROPERTY_MESSAGE);
                assertWithMessage("Unexpected output log")
                    .that(systemOut.getLog())
                    .isEqualTo("");
            }
        });
        Main.main("-c", getPath("InputMainConfig-classname-prop.xml"),
                "-p", getPath("InputMainPropertyChainingUndefinedProperty.properties"),
                getPath("InputMain.java"));
    }

    @Test
    public void testExistingTargetFilePlainOutputNonexistentProperties()
            throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Could not find file 'nonexistent.properties'."
                        + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-classname-prop.xml"),
                "-p", "nonexistent.properties",
                getPath("InputMain.java"));
    }

    @Test
    public void testExistingIncorrectConfigFile()
            throws Exception {
        exit.expectSystemExitWithStatus(-2);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                final String errorOutput = "com.puppycrawl.tools.checkstyle.api."
                        + "CheckstyleException: unable to parse configuration stream - ";
                    assertTrue(systemErr.getLog().startsWith(errorOutput), "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-Incorrect.xml"),
            getPath("InputMain.java"));
    }

    @Test
    public void testExistingIncorrectChildrenInConfigFile()
            throws Exception {
        exit.expectSystemExitWithStatus(-2);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                final String errorOutput = "com.puppycrawl.tools.checkstyle.api."
                        + "CheckstyleException: cannot initialize module RegexpSingleline"
                        + " - RegexpSingleline is not allowed as a child in RegexpSingleline";
                assertTrue(systemErr.getLog().startsWith(errorOutput), "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-incorrectChildren.xml"),
            getPath("InputMain.java"));
    }

    @Test
    public void testExistingIncorrectChildrenInConfigFile2()
            throws Exception {
        exit.expectSystemExitWithStatus(-2);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                final String errorOutput = "com.puppycrawl.tools.checkstyle.api."
                        + "CheckstyleException: cannot initialize module TreeWalker - "
                        + "cannot initialize module JavadocMethod - "
                        + "JavadocVariable is not allowed as a child in JavadocMethod";
                assertTrue(systemErr.getLog().startsWith(errorOutput), "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-incorrectChildren2.xml"),
            getPath("InputMain.java"));
    }

    @Test
    public void testLoadPropertiesIoException() throws Exception {
        final Class<?>[] param = new Class<?>[1];
        param[0] = File.class;
        final Class<?> cliOptionsClass = Class.forName(Main.class.getName());
        final Method method = cliOptionsClass.getDeclaredMethod("loadProperties", param);
        method.setAccessible(true);
        try {
            method.invoke(null, new File("."));
            fail("Exception was expected");
        }
        catch (InvocationTargetException ex) {
            assertTrue(ex.getCause() instanceof CheckstyleException,
                    "Invalid error cause");
            // We do separate validation for message as in Windows
            // disk drive letter appear in message,
            // so we skip that drive letter for compatibility issues
            final Violation loadPropertiesMessage = new Violation(1,
                    Definitions.CHECKSTYLE_BUNDLE, Main.LOAD_PROPERTIES_EXCEPTION,
                    new String[] {""}, null, getClass(), null);
            final String causeMessage = ex.getCause().getLocalizedMessage();
            final String violation = loadPropertiesMessage.getViolation();
            final boolean samePrefix = causeMessage.substring(0, causeMessage.indexOf(' '))
                    .equals(violation
                            .substring(0, violation.indexOf(' ')));
            final boolean sameSuffix =
                    causeMessage.substring(causeMessage.lastIndexOf(' '))
                    .equals(violation
                            .substring(violation.lastIndexOf(' ')));
            assertTrue(samePrefix || sameSuffix, "Invalid violation");
            assertTrue(causeMessage.contains(".'"), "Invalid violation");
        }
    }

    @Test
    public void testExistingDirectoryWithViolations() throws Exception {
        // we just reference there all violations
        final String[][] outputValues = {
                {"InputMainComplexityOverflow", "1", "172"},
        };

        final int allowedLength = 170;
        final String msgKey = "maxLen.file";
        final String bundle = "com.puppycrawl.tools.checkstyle.checks.sizes.messages";

        Main.main("-c", getPath("InputMainConfig-filelength.xml"),
                getPath(""));
        final String expectedPath = getFilePath("") + File.separator;
        final StringBuilder sb = new StringBuilder(28);
        sb.append(auditStartMessage.getViolation())
                .append(EOL);
        final String format = "[WARN] " + expectedPath + outputValues[0][0] + ".java:"
                + outputValues[0][1] + ": ";
        for (String[] outputValue : outputValues) {
            final String violation = new Violation(1, bundle,
                    msgKey, new Integer[] {Integer.valueOf(outputValue[2]), allowedLength},
                    null, getClass(), null).getViolation();
            final String line = format + violation + " [FileLength]";
            sb.append(line).append(EOL);
        }
        sb.append(auditFinishMessage.getViolation())
                .append(EOL);
        assertEquals(sb.toString(), systemOut.getLog(), "Unexpected output log");
        assertEquals("", systemErr.getLog(), "Unexpected system error log");
    }

    /**
     * Test doesn't need to be serialized.
     *
     * @noinspection SerializableInnerClassWithNonSerializableOuterClass
     */
    @Test
    public void testListFilesNotFile() throws Exception {
        final File fileMock = new File("") {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean canRead() {
                return true;
            }

            @Override
            public boolean isDirectory() {
                return false;
            }

            @Override
            public boolean isFile() {
                return false;
            }
        };

        final List<File> result = Whitebox.invokeMethod(Main.class, "listFiles",
                fileMock, new ArrayList<Pattern>());
        assertEquals(0, result.size(), "Invalid result size");
    }

    /**
     * Test doesn't need to be serialized.
     *
     * @noinspection SerializableInnerClassWithNonSerializableOuterClass
     */
    @Test
    public void testListFilesDirectoryWithNull() throws Exception {
        final File[] nullResult = null;
        final File fileMock = new File("") {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean canRead() {
                return true;
            }

            @Override
            public boolean isDirectory() {
                return true;
            }

            @Override
            public File[] listFiles() {
                return nullResult;
            }
        };

        final List<File> result = Whitebox.invokeMethod(Main.class, "listFiles",
                fileMock, new ArrayList<Pattern>());
        assertEquals(0, result.size(), "Invalid result size");
    }

    @Test
    public void testFileReferenceDuringException() throws Exception {
        exit.expectSystemExitWithStatus(-2);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                final String exceptionMessage = addEndOfLine("com.puppycrawl.tools.checkstyle.api."
                        + "CheckstyleException: Exception was thrown while processing "
                        + new File(getNonCompilablePath("InputMainIncorrectClass.java")).getPath());
                assertTrue(systemErr.getLog().contains(exceptionMessage),
                        "Unexpected system error log");
            }
        });

        // We put xml as source to cause parse exception
        Main.main("-c", getPath("InputMainConfig-classname.xml"),
                getNonCompilablePath("InputMainIncorrectClass.java"));
    }

    @Test
    public void testRemoveLexerDefaultErrorListener() {
        assertExitWithStatus(-2, () -> {
            invokeMain("-t", getNonCompilablePath("InputMainIncorrectClass.java"));
        });

        assertWithMessage("First line of exception message should not contain lexer error.")
            .that(systemErr.getCapturedData().startsWith("line 2:2 token recognition error"))
                .isFalse();
    }

    @Test
    public void testRemoveParserDefaultErrorListener() {
        assertExitWithStatus(-2, () -> {
            invokeMain("-t", getNonCompilablePath("InputMainIncorrectClass.java"));
        });

        final String capturedData = systemErr.getCapturedData();

        assertWithMessage("First line of exception message should not contain parser error.")
            .that(capturedData.startsWith("line 2:0 no viable alternative"))
                .isFalse();
        assertWithMessage("Second line of exception message should not contain parser error.")
            .that(capturedData.startsWith("line 2:0 no viable alternative",
                    capturedData.indexOf('\n') + 1))
                .isFalse();
    }

    @Test
    public void testPrintTreeOnMoreThanOneFile() {
        assertExitWithStatus(-1, () -> invokeMain("-t", getPath("")));
        assertEquals("Printing AST is allowed for only one file."
            + System.lineSeparator(), systemOut.getCapturedData(), "Unexpected output log");
        assertEquals("", systemErr.getCapturedData(), "Unexpected system error log");
    }

    @Test
    public void testPrintTreeOption() throws Exception {
        final String expected = addEndOfLine(
            "COMPILATION_UNIT -> COMPILATION_UNIT [1:0]",
            "|--PACKAGE_DEF -> package [1:0]",
            "|   |--ANNOTATIONS -> ANNOTATIONS [1:39]",
            "|   |--DOT -> . [1:39]",
            "|   |   |--DOT -> . [1:28]",
            "|   |   |   |--DOT -> . [1:22]",
            "|   |   |   |   |--DOT -> . [1:11]",
            "|   |   |   |   |   |--IDENT -> com [1:8]",
            "|   |   |   |   |   `--IDENT -> puppycrawl [1:12]",
            "|   |   |   |   `--IDENT -> tools [1:23]",
            "|   |   |   `--IDENT -> checkstyle [1:29]",
            "|   |   `--IDENT -> main [1:40]",
            "|   `--SEMI -> ; [1:44]",
            "|--CLASS_DEF -> CLASS_DEF [3:0]",
            "|   |--MODIFIERS -> MODIFIERS [3:0]",
            "|   |   `--LITERAL_PUBLIC -> public [3:0]",
            "|   |--LITERAL_CLASS -> class [3:7]",
            "|   |--IDENT -> InputMain [3:13]",
            "|   `--OBJBLOCK -> OBJBLOCK [3:23]",
            "|       |--LCURLY -> { [3:23]",
            "|       `--RCURLY -> } [4:0]",
            "`--CLASS_DEF -> CLASS_DEF [5:0]",
            "    |--MODIFIERS -> MODIFIERS [5:0]",
            "    |--LITERAL_CLASS -> class [5:0]",
            "    |--IDENT -> InputMainInner [5:6]",
            "    `--OBJBLOCK -> OBJBLOCK [5:21]",
            "        |--LCURLY -> { [5:21]",
            "        `--RCURLY -> } [6:0]");

        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(expected, systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-t", getPath("InputMain.java"));
    }

    @Test
    public void testPrintXpathOption() throws Exception {
        final String expected = addEndOfLine(
            "COMPILATION_UNIT -> COMPILATION_UNIT [1:0]",
            "|--CLASS_DEF -> CLASS_DEF [3:0]",
            "|   `--OBJBLOCK -> OBJBLOCK [3:28]",
            "|       |--METHOD_DEF -> METHOD_DEF [4:4]",
            "|       |   `--SLIST -> { [4:20]",
            "|       |       |--VARIABLE_DEF -> VARIABLE_DEF [5:8]",
            "|       |       |   |--IDENT -> a [5:12]");
        Main.main("-b", "/COMPILATION_UNIT/CLASS_DEF//METHOD_DEF[./IDENT[@text='methodOne']]"
                        + "//VARIABLE_DEF/IDENT",
                getPath("InputMainXPath.java"));
        assertThat("Unexpected output log", systemOut.getCapturedData(), is(expected));
        assertThat("Unexpected system error log", systemErr.getCapturedData(), is(""));
    }

    @Test
    public void testPrintXpathCommentNode() throws Exception {
        final String expected = addEndOfLine(
            "COMPILATION_UNIT -> COMPILATION_UNIT [1:0]",
            "`--CLASS_DEF -> CLASS_DEF [17:0]",
            "    `--OBJBLOCK -> OBJBLOCK [17:19]",
            "        |--CTOR_DEF -> CTOR_DEF [19:4]",
            "        |   |--BLOCK_COMMENT_BEGIN -> /* [18:4]");
        Main.main("-b", "/COMPILATION_UNIT/CLASS_DEF//BLOCK_COMMENT_BEGIN",
                getPath("InputMainXPath.java"));
        assertThat("Unexpected output log", systemOut.getCapturedData(), is(expected));
        assertThat("Unexpected system error log", systemErr.getCapturedData(), is(""));
    }

    @Test
    public void testPrintXpathNodeParentNull() throws IOException {
        final String expected = addEndOfLine("COMPILATION_UNIT -> COMPILATION_UNIT [1:0]");
        Main.main("-b", "/COMPILATION_UNIT", getPath("InputMainXPath.java"));
        assertThat("Unexpected output log", systemOut.getCapturedData(), is(expected));
        assertThat("Unexpected system error log", systemErr.getCapturedData(), is(""));
    }

    @Test
    public void testPrintXpathFullOption() throws Exception {
        final String expected = addEndOfLine(
            "COMPILATION_UNIT -> COMPILATION_UNIT [1:0]",
            "|--CLASS_DEF -> CLASS_DEF [3:0]",
            "|   `--OBJBLOCK -> OBJBLOCK [3:28]",
            "|       |--METHOD_DEF -> METHOD_DEF [8:4]",
            "|       |   `--SLIST -> { [8:26]",
            "|       |       |--VARIABLE_DEF -> VARIABLE_DEF [9:8]",
            "|       |       |   |--IDENT -> a [9:12]");
        final String xpath = "/COMPILATION_UNIT/CLASS_DEF//METHOD_DEF[./IDENT[@text='method']]"
                + "//VARIABLE_DEF/IDENT";
        Main.main("--branch-matching-xpath", xpath, getPath("InputMainXPath.java"));
    }

    @Test
    public void testPrintXpathTwoResults() throws Exception {
        final String expected = addEndOfLine(
            "COMPILATION_UNIT -> COMPILATION_UNIT [1:0]",
            "|--CLASS_DEF -> CLASS_DEF [12:0]",
            "|   `--OBJBLOCK -> OBJBLOCK [12:10]",
            "|       |--METHOD_DEF -> METHOD_DEF [13:4]",
            "---------",
            "COMPILATION_UNIT -> COMPILATION_UNIT [1:0]",
            "|--CLASS_DEF -> CLASS_DEF [12:0]",
            "|   `--OBJBLOCK -> OBJBLOCK [12:10]",
            "|       |--METHOD_DEF -> METHOD_DEF [14:4]");
        Main.main("--branch-matching-xpath", "/COMPILATION_UNIT/CLASS_DEF[./IDENT[@text='Two']]"
                        + "//METHOD_DEF",
                getPath("InputMainXPath.java"));
        assertThat("Unexpected output log", systemOut.getCapturedData(), is(expected));
        assertThat("Unexpected system error log", systemErr.getCapturedData(), is(""));
    }

    @Test
    public void testPrintXpathInvalidXpath() throws Exception {
        final String invalidXpath = "\\/COMPILATION_UNIT/CLASS_DEF[./IDENT[@text='Two']]"
                + "//METHOD_DEF";
        final String filePath = getFilePath("InputMainXPath.java");
        exit.expectSystemExitWithStatus(-2);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                final String exceptionFirstLine = "com.puppycrawl.tools.checkstyle.api."
                    + "CheckstyleException: Error during evaluation for xpath: " + invalidXpath
                    + ", file: " + filePath + EOL;
                assertThat("Unexpected system error log",
                    systemErr.getLog().startsWith(exceptionFirstLine), is(true));
            }
        });
        Main.main("--branch-matching-xpath", invalidXpath, filePath);
    }

    @Test
    public void testPrintTreeCommentsOption() throws Exception {
        final String expected = addEndOfLine(
            "COMPILATION_UNIT -> COMPILATION_UNIT [1:0]",
            "|--PACKAGE_DEF -> package [1:0]",
            "|   |--ANNOTATIONS -> ANNOTATIONS [1:39]",
            "|   |--DOT -> . [1:39]",
            "|   |   |--DOT -> . [1:28]",
            "|   |   |   |--DOT -> . [1:22]",
            "|   |   |   |   |--DOT -> . [1:11]",
            "|   |   |   |   |   |--IDENT -> com [1:8]",
            "|   |   |   |   |   `--IDENT -> puppycrawl [1:12]",
            "|   |   |   |   `--IDENT -> tools [1:23]",
            "|   |   |   `--IDENT -> checkstyle [1:29]",
            "|   |   `--IDENT -> main [1:40]",
            "|   `--SEMI -> ; [1:44]",
            "|--CLASS_DEF -> CLASS_DEF [3:0]",
            "|   |--MODIFIERS -> MODIFIERS [3:0]",
            "|   |   |--BLOCK_COMMENT_BEGIN -> /* [2:0]",
            "|   |   |   |--COMMENT_CONTENT -> comment [2:2]",
            "|   |   |   `--BLOCK_COMMENT_END -> */ [2:8]",
            "|   |   `--LITERAL_PUBLIC -> public [3:0]",
            "|   |--LITERAL_CLASS -> class [3:7]",
            "|   |--IDENT -> InputMain [3:13]",
            "|   `--OBJBLOCK -> OBJBLOCK [3:23]",
            "|       |--LCURLY -> { [3:23]",
            "|       `--RCURLY -> } [4:0]",
            "`--CLASS_DEF -> CLASS_DEF [5:0]",
            "    |--MODIFIERS -> MODIFIERS [5:0]",
            "    |--LITERAL_CLASS -> class [5:0]",
            "    |--IDENT -> InputMainInner [5:6]",
            "    `--OBJBLOCK -> OBJBLOCK [5:21]",
            "        |--LCURLY -> { [5:21]",
            "        `--RCURLY -> } [6:0]");

        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(expected, systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-T", getPath("InputMain.java"));
    }

    @Test
    public void testPrintTreeJavadocOption() throws Exception {
        final String expected = new String(Files7.readAllBytes(Paths.get(
            getPath("InputMainExpectedInputJavadocComment.txt"))), StandardCharsets.UTF_8)
            .replaceAll("\\\\r\\\\n", "\\\\n").replaceAll("\r\n", "\n");

        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(expected, systemOut.getLog().replaceAll("\\\\r\\\\n", "\\\\n")
                                .replaceAll("\r\n", "\n"), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-j", getPath("InputMainJavadocComment.javadoc"));
    }

    @Test
    public void testPrintSuppressionOption() throws Exception {
        final String expected = addEndOfLine(
            "/COMPILATION_UNIT/CLASS_DEF[./IDENT[@text='InputMainSuppressionsStringPrinter']]",
                "/COMPILATION_UNIT/CLASS_DEF[./IDENT[@text='InputMainSuppressionsStringPrinter']]"
                        + "/MODIFIERS",
                "/COMPILATION_UNIT/CLASS_DEF[./IDENT[@text='InputMainSuppressionsStringPrinter']"
                        + "]/LITERAL_CLASS");

        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(expected, systemOut.getLog(),
                        "Unexpected output log");
                assertEquals("", systemErr.getLog(),
                        "Unexpected system error log");
            }
        });
        Main.main(getPath("InputMainSuppressionsStringPrinter.java"),
                "-s", "3:1");
    }

    @Test
    public void testPrintSuppressionAndTabWidthOption() throws Exception {
        final String expected = addEndOfLine(
            "/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainSuppressionsStringPrinter']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='getName']]"
                    + "/SLIST/VARIABLE_DEF[./IDENT[@text='var']]",
                "/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainSuppressionsStringPrinter']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='getName']]/SLIST"
                    + "/VARIABLE_DEF[./IDENT[@text='var']]/MODIFIERS",
                "/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainSuppressionsStringPrinter']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='getName']]/SLIST"
                    + "/VARIABLE_DEF[./IDENT[@text='var']]/TYPE",
                "/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainSuppressionsStringPrinter']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='getName']]/SLIST"
                    + "/VARIABLE_DEF[./IDENT[@text='var']]/TYPE/LITERAL_INT");

        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(expected, systemOut.getLog(),
                        "Unexpected output log");
                assertEquals("", systemErr.getLog(),
                        "Unexpected system error log");
            }
        });
        Main.main(getPath("InputMainSuppressionsStringPrinter.java"),
                "-s", "7:9", "--tabWidth", "2");
    }

    @Test
    public void testPrintSuppressionConflictingOptionsTvsC() throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Option '-s' cannot be used with other options."
                        + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });

        Main.main("-c", "/google_checks.xml",
                getPath(""), "-s", "2:4");
    }

    @Test
    public void testPrintSuppressionConflictingOptionsTvsP() throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Option '-s' cannot be used with other options."
                        + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });

        Main.main("-p", getPath("InputMainMycheckstyle.properties"), "-s", "2:4", getPath(""));
    }

    @Test
    public void testPrintSuppressionConflictingOptionsTvsF() throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Option '-s' cannot be used with other options."
                        + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });

        Main.main("-f", "plain", "-s", "2:4", getPath(""));
    }

    @Test
    public void testPrintSuppressionConflictingOptionsTvsO() throws Exception {
        final File file = temporaryFolder.newFile("file.output");

        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Option '-s' cannot be used with other options."
                        + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });

        Main.main("-o", file.getCanonicalPath(), "-s", "2:4", getPath(""));
    }

    @Test
    public void testPrintSuppressionOnMoreThanOneFile() throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Printing xpath suppressions is allowed for "
                        + "only one file."
                        + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });

        Main.main("-s", "2:4", getPath(""), getPath(""));
    }

    @Test
    public void testGenerateXpathSuppressionOptionOne() throws Exception {
        final String expected = addEndOfLine(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<!DOCTYPE suppressions PUBLIC",
                "    \"-//Checkstyle//DTD SuppressionXpathFilter Experimental Configuration 1.2"
                    + "//EN\"",
                "    \"https://checkstyle.org/dtds/suppressions_1_2_xpath_experimental.dtd\">",
                "<suppressions>",
                "<suppress-xpath",
                "       files=\"InputMainComplexityOverflow.java\"",
                "       checks=\"MissingJavadocMethodCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainComplexityOverflow']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='provokeNpathIntegerOverflow']]\"/>",
                "<suppress-xpath",
                "       files=\"InputMainComplexityOverflow.java\"",
                "       checks=\"LeftCurlyCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainComplexityOverflow']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='provokeNpathIntegerOverflow']]/SLIST\"/>",
                "<suppress-xpath",
                "       files=\"InputMainComplexityOverflow.java\"",
                "       checks=\"EmptyBlockCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainComplexityOverflow']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='provokeNpathIntegerOverflow']]/SLIST/LITERAL_IF"
                    + "/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST"
                    + "/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST\"/>",
                "<suppress-xpath",
                "       files=\"InputMainComplexityOverflow.java\"",
                "       checks=\"EmptyBlockCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainComplexityOverflow']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='provokeNpathIntegerOverflow']]/SLIST/LITERAL_IF"
                    + "/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST"
                    + "/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST\"/>",
                "<suppress-xpath",
                "       files=\"InputMainComplexityOverflow.java\"",
                "       checks=\"EmptyBlockCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainComplexityOverflow']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='provokeNpathIntegerOverflow']]/SLIST/LITERAL_IF"
                    + "/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST"
                    + "/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST\"/>",
                "<suppress-xpath",
                "       files=\"InputMainComplexityOverflow.java\"",
                "       checks=\"EmptyBlockCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainComplexityOverflow']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='provokeNpathIntegerOverflow']]/SLIST/LITERAL_IF"
                    + "/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST"
                    + "/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST\"/>",
                "<suppress-xpath",
                "       files=\"InputMainComplexityOverflow.java\"",
                "       checks=\"EmptyBlockCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainComplexityOverflow']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='provokeNpathIntegerOverflow']]/SLIST/LITERAL_IF"
                    + "/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST"
                    + "/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST\"/>",
                "<suppress-xpath",
                "       files=\"InputMainComplexityOverflow.java\"",
                "       checks=\"EmptyBlockCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainComplexityOverflow']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='provokeNpathIntegerOverflow']]/SLIST/LITERAL_IF"
                    + "/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST"
                    + "/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST\"/>",
                "<suppress-xpath",
                "       files=\"InputMainComplexityOverflow.java\"",
                "       checks=\"EmptyBlockCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainComplexityOverflow']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='provokeNpathIntegerOverflow']]/SLIST/LITERAL_IF"
                    + "/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST"
                    + "/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST\"/>",
                "<suppress-xpath",
                "       files=\"InputMainComplexityOverflow.java\"",
                "       checks=\"EmptyBlockCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainComplexityOverflow']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='provokeNpathIntegerOverflow']]/SLIST/LITERAL_IF"
                    + "/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST"
                    + "/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST\"/>",
                "<suppress-xpath",
                "       files=\"InputMainComplexityOverflow.java\"",
                "       checks=\"EmptyBlockCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainComplexityOverflow']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='provokeNpathIntegerOverflow']]/SLIST/LITERAL_IF"
                    + "/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST"
                    + "/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST\"/>",
                "<suppress-xpath",
                "       files=\"InputMainComplexityOverflow.java\"",
                "       checks=\"EmptyBlockCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                    + "[./IDENT[@text='InputMainComplexityOverflow']]/OBJBLOCK"
                    + "/METHOD_DEF[./IDENT[@text='provokeNpathIntegerOverflow']]/SLIST/LITERAL_IF"
                    + "/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST"
                    + "/LITERAL_IF/SLIST/LITERAL_IF/SLIST/LITERAL_IF/SLIST\"/>",
                "</suppressions>");

        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(expected, systemOut.getLog(),
                        "Unexpected output log");
                assertEquals("", systemErr.getLog(),
                        "Unexpected system error log");
            }
        });
        Main.main("-c", "/google_checks.xml", "--generate-xpath-suppression",
                getPath("InputMainComplexityOverflow.java"));
    }

    @Test
    public void testGenerateXpathSuppressionOptionTwo() throws Exception {
        final String expected = addEndOfLine(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<!DOCTYPE suppressions PUBLIC",
            "    \"-//Checkstyle//DTD SuppressionXpathFilter Experimental Configuration 1.2"
                + "//EN\"",
            "    \"https://checkstyle.org/dtds/suppressions_1_2_xpath_experimental.dtd\">",
            "<suppressions>",
            "<suppress-xpath",
            "       files=\"InputMainGenerateXpathSuppressions.java\"",
            "       checks=\"ExplicitInitializationCheck\"",
            "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                + "[./IDENT[@text='InputMainGenerateXpathSuppressions']]"
                + "/OBJBLOCK/VARIABLE_DEF/IDENT[@text='low']\"/>",
            "<suppress-xpath",
            "       files=\"InputMainGenerateXpathSuppressions.java\"",
            "       checks=\"IllegalThrowsCheck\"",
            "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                + "[./IDENT[@text='InputMainGenerateXpathSuppressions']]"
                + "/OBJBLOCK/METHOD_DEF[./IDENT[@text='test']]/LITERAL_THROWS"
                + "/IDENT[@text='RuntimeException']\"/>",
            "<suppress-xpath",
            "       files=\"InputMainGenerateXpathSuppressions.java\"",
            "       checks=\"NestedForDepthCheck\"",
            "       query=\"/COMPILATION_UNIT/CLASS_DEF"
                + "[./IDENT[@text='InputMainGenerateXpathSuppressions']]"
                + "/OBJBLOCK/METHOD_DEF[./IDENT[@text='test']]/SLIST/LITERAL_FOR/SLIST"
                + "/LITERAL_FOR/SLIST/LITERAL_FOR\"/>",
            "</suppressions>");

        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(expected, systemOut.getLog(),
                        "Unexpected output log");
                assertEquals("", systemErr.getLog(),
                        "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-xpath-suppressions.xml"),
                "--generate-xpath-suppression",
                getPath("InputMainGenerateXpathSuppressions.java"));
    }

    @Test
    public void testGenerateXpathSuppressionOptionEmptyConfig() throws Exception {
        final String expected = "";

        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(expected, systemOut.getLog(),
                        "Unexpected output log");
                assertEquals("", systemErr.getLog(),
                        "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-empty.xml"), "--generate-xpath-suppression",
                getPath("InputMainComplexityOverflow.java"));
    }

    @Test
    public void testGenerateXpathSuppressionOptionCustomOutput() throws Exception {
        final String expected = addEndOfLine(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<!DOCTYPE suppressions PUBLIC",
                "    \"-//Checkstyle//DTD SuppressionXpathFilter Experimental Configuration 1.2"
                    + "//EN\"",
                "    \"https://checkstyle.org/dtds/suppressions_1_2_xpath_experimental.dtd\">",
                "<suppressions>",
                "<suppress-xpath",
                "       files=\"InputMainGenerateXpathSuppressionsTabWidth.java\"",
                "       checks=\"ExplicitInitializationCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF[./IDENT["
                    + "@text='InputMainGenerateXpathSuppressionsTabWidth']]"
                    + "/OBJBLOCK/VARIABLE_DEF/IDENT[@text='low']\"/>",
                "</suppressions>");
        final File file = temporaryFolder.newFile();
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() throws IOException {
                final BufferedReader br = Files7.newBufferedReader(new Path(file));
                try {
                    final StringBuilder sb = new StringBuilder(50);
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                        sb.append(EOL);
                    }
                    final String fileContent = sb.toString();
                    assertEquals(expected, fileContent,
                            "Unexpected output log");
                    assertEquals("", systemErr.getLog(),
                            "Unexpected system error log");
                }
                finally {
                    br.close();
                }
            }
        });
        Main.main("-c", getPath("InputMainConfig-xpath-suppressions.xml"),
                "-o", file.getPath(),
                "--generate-xpath-suppression",
                getPath("InputMainGenerateXpathSuppressionsTabWidth.java"));
    }

    @Test
    public void testGenerateXpathSuppressionOptionDefaultTabWidth() throws Exception {
        final String expected = addEndOfLine(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<!DOCTYPE suppressions PUBLIC",
                "    \"-//Checkstyle//DTD SuppressionXpathFilter Experimental Configuration 1.2"
                    + "//EN\"",
                "    \"https://checkstyle.org/dtds/suppressions_1_2_xpath_experimental.dtd\">",
                "<suppressions>",
                "<suppress-xpath",
                "       files=\"InputMainGenerateXpathSuppressionsTabWidth.java\"",
                "       checks=\"ExplicitInitializationCheck\"",
                "       query=\"/COMPILATION_UNIT/CLASS_DEF[./IDENT["
                    + "@text='InputMainGenerateXpathSuppressionsTabWidth']]"
                    + "/OBJBLOCK/VARIABLE_DEF/IDENT[@text='low']\"/>",
                "</suppressions>");

        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(expected, systemOut.getLog(),
                        "Unexpected output log");
                assertEquals("", systemErr.getLog(),
                        "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-xpath-suppressions.xml"),
                "--generate-xpath-suppression",
                getPath("InputMainGenerateXpathSuppressionsTabWidth.java"));
    }

    @Test
    public void testGenerateXpathSuppressionOptionCustomTabWidth() throws Exception {
        final String expected = "";

        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(expected, systemOut.getLog(),
                        "Unexpected output log");
                assertEquals("", systemErr.getLog(),
                        "Unexpected system error log");
            }
        });
        Main.main("-c", getPath("InputMainConfig-xpath-suppressions.xml"),
                "--generate-xpath-suppression",
                "--tabWidth", "20",
                getPath("InputMainGenerateXpathSuppressionsTabWidth.java"));
    }

    @Test
    public void testPrintFullTreeOption() throws Exception {
        final String expected = new String(Files7.readAllBytes(Paths.get(
            getPath("InputMainExpectedInputAstTreeStringPrinterJavadoc.txt"))),
            StandardCharsets.UTF_8).replaceAll("\\\\r\\\\n", "\\\\n")
                .replaceAll("\r\n", "\n");

        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals(expected, systemOut.getLog().replaceAll("\\\\r\\\\n", "\\\\n")
                                .replaceAll("\r\n", "\n"), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-J", getPath("InputMainAstTreeStringPrinterJavadoc.java"));
    }

    @Test
    public void testConflictingOptionsTvsC() throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Option '-t' cannot be used with other options."
                    + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });

        Main.main("-c", "/google_checks.xml", "-t", getPath(""));
    }

    @Test
    public void testConflictingOptionsTvsP() throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Option '-t' cannot be used with other options."
                    + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });

        Main.main("-p", getPath("InputMainMycheckstyle.properties"), "-t", getPath(""));
    }

    @Test
    public void testConflictingOptionsTvsF() throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Option '-t' cannot be used with other options."
                    + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });

        Main.main("-f", "plain", "-t", getPath(""));
    }

    @Test
    public void testConflictingOptionsTvsS() throws Exception {
        final File file = temporaryFolder.newFile("file.output");

        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Option '-t' cannot be used with other options."
                        + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });

        Main.main("-s", file.getCanonicalPath(), "-t", getPath(""));
    }

    @Test
    public void testConflictingOptionsTvsO() throws Exception {
        final File file = temporaryFolder.newFile("file.output");

        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Option '-t' cannot be used with other options."
                    + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });

        Main.main("-o", file.getCanonicalPath(), "-t", getPath(""));
    }

    @Test
    public void testDebugOption() throws Exception {
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertNotEquals("Unexpected system error log", "", systemErr.getLog());
            }
        });
        Main.main("-c", "/google_checks.xml", getPath("InputMain.java"), "-d");
    }

    @Test
    public void testExcludeOption() throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Files to process must be specified, found 0."
                    + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", "/google_checks.xml", getFilePath(""), "-e", getFilePath(""));
    }

    @Test
    public void testExcludeOptionFile() throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Files to process must be specified, found 0."
                    + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
            }
        });
        Main.main("-c", "/google_checks.xml", getFilePath("InputMain.java"), "-e",
                getFilePath("InputMain.java"));
    }

    @Test
    public void testExcludeRegexpOption() throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Files to process must be specified, found 0."
                    + System7.lineSeparator(), systemOut.getLog(),
                    "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected output log");
            }
        });
        Main.main("-c", "/google_checks.xml", getFilePath(""), "-x", ".");
    }

    @Test
    public void testExcludeRegexpOptionFile() throws Exception {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("Files to process must be specified, found 0."
                    + System7.lineSeparator(), systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected output log");
            }
        });
        Main.main("-c", "/google_checks.xml", getFilePath("InputMain.java"), "-x", ".");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testExcludeDirectoryNotMatch() throws Exception {
        final Class<?> optionsClass = Class.forName(Main.class.getName());
        final Method method = optionsClass.getDeclaredMethod("listFiles", File.class, List.class);
        method.setAccessible(true);
        final List<Pattern> list = new ArrayList<Pattern>();
        list.add(Pattern.compile("BAD_PATH"));

        final List<File> result = (List<File>) method.invoke(null, new File(getFilePath("")),
                list);
        assertNotEquals(0, result.size(), "Invalid result size");
    }

    @Test
    public void testCustomRootModule() throws Exception {
        TestRootModuleChecker.reset();

        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                assertEquals("", systemOut.getLog(), "Unexpected output log");
                assertEquals("", systemErr.getLog(), "Unexpected system error log");
                assertTrue(TestRootModuleChecker.isProcessed(), "Invalid Checker state");
            }
        });
        Main.main("-c", getPath("InputMainConfig-custom-root-module.xml"),
                getPath("InputMain.java"));
        assertTrue(TestRootModuleChecker.isDestroyed(), "RootModule should be destroyed");
    }

    @Test
    public void testCustomSimpleRootModule() throws Exception {
        exit.expectSystemExitWithStatus(-2);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                final String checkstylePackage = "com.puppycrawl.tools.checkstyle.";
                final Violation unableToInstantiateExceptionMessage = new Violation(1,
                        Definitions.CHECKSTYLE_BUNDLE,
                        "PackageObjectFactory.unableToInstantiateExceptionMessage",
                        new String[] {"TestRootModuleChecker", checkstylePackage
                                + "TestRootModuleChecker, "
                                + "TestRootModuleCheckerCheck, " + checkstylePackage
                                + "TestRootModuleCheckerCheck"},
                        null, getClass(), null);
                assertTrue(systemErr.getLog().startsWith(checkstylePackage
                        + "api.CheckstyleException: " + unableToInstantiateExceptionMessage.getViolation()),
                        "Unexpected system error log");
                assertFalse(TestRootModuleChecker.isProcessed(), "Invalid checker state");
            }
        });
        TestRootModuleChecker.reset();
        Main.main("-c", getPath("InputMainConfig-custom-simple-root-module.xml"),
                getPath("InputMain.java"));
    }

    @Test
    public void testExceptionOnExecuteIgnoredModuleWithUnknownModuleName() throws Exception {
        exit.expectSystemExitWithStatus(-2);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                final String cause = "com.puppycrawl.tools.checkstyle.api.CheckstyleException:"
                        + " cannot initialize module TreeWalker - ";
                assertTrue(systemErr.getLog().startsWith(cause), "Unexpected system error log");
            }
        });

        Main.main("-c", getPath("InputMainConfig-non-existent-classname-ignore.xml"),
                "--executeIgnoredModules",
                getPath("InputMain.java"));
    }

    @Test
    public void testExceptionOnExecuteIgnoredModuleWithBadPropertyValue() throws Exception {
        exit.expectSystemExitWithStatus(-2);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                final String cause = "com.puppycrawl.tools.checkstyle.api.CheckstyleException:"
                        + " cannot initialize module TreeWalker - ";
                final String causeDetail = "it is not a boolean";
                assertTrue(systemErr.getLog().startsWith(cause), "Unexpected system error log");
                assertTrue(systemErr.getLog().contains(causeDetail), "Unexpected system error log");
            }
        });

        Main.main("-c", getPath("InputMainConfig-TypeName-bad-value.xml"),
                "--executeIgnoredModules",
                getPath("InputMain.java"));
    }

    @Test
    public void testMissingFiles() throws IOException {
        exit.expectSystemExitWithStatus(-1);
        exit.checkAssertionAfterwards(new Assertion() {
            @Override
            public void checkAssertion() {
                final String usage = "Missing required parameter: '<files>'" + EOL + SHORT_USAGE;
                assertEquals("", systemOut.getLog(), "Unexpected output log");
                assertEquals(usage, systemErr.getLog(), "Unexpected system error log");
            }
        });

        Main.main();
    }

    @Test
    public void testOutputFormatToStringLowercase() {
        assertEquals("xml", Main.OutputFormat.XML.toString(), "expected xml");
        assertEquals("plain", Main.OutputFormat.PLAIN.toString(), "expected plain");
    }

    @Test
    public void testXmlOutputFormatCreateListener() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final AuditListener listener = Main.OutputFormat.XML.createListener(out,
                AutomaticBean.OutputStreamOptions.CLOSE);
        assertTrue(listener instanceof XMLLogger, "listener is XMLLogger");
    }

    @Test
    public void testSarifOutputFormatCreateListener() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final AuditListener listener = Main.OutputFormat.SARIF.createListener(out,
                AutomaticBean.OutputStreamOptions.CLOSE);
        assertTrue(listener instanceof SarifLogger, "listener is SarifLogger");
    }

    @Test
    public void testPlainOutputFormatCreateListener() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final AuditListener listener = Main.OutputFormat.PLAIN.createListener(out,
                AutomaticBean.OutputStreamOptions.CLOSE);
        assertTrue(listener instanceof DefaultLogger, "listener is DefaultLogger");
    }

}
