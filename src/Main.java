import java.beans.XMLEncoder;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Trap;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.internal.JThrowStmt;
import soot.options.Options;
import soot.toolkits.graph.TrapUnitGraph;
import soot.toolkits.graph.UnitGraph;
import java.sql.Connection;
import java.sql.SQLException;

import com.thoughtworks.xstream.XStream;

import entities.*;
import entities.Exception;

public class Main {
	public static void main(String[] args) throws java.lang.Exception {
		List<String> process_dirs = new LinkedList<String>();

		String sep = System.getProperty("file.separator");
		String process_dir = System.getProperty("user.dir") + sep + "src" + sep + "teste-bin";
		process_dirs.add(process_dir);

		Options.v().set_process_dir(process_dirs);
		Options.v().set_soot_classpath(process_dir);
		Options.v().set_prepend_classpath(true);

		Scene.v().loadNecessaryClasses();
		run(); //processa o bytecode e cria as entidades

		String xml = getXML();

		// Escreve no arquivo output.xml
		FileOutputStream fos = new FileOutputStream("output.xml");
		fos.write(xml.getBytes());
		fos.flush();
		fos.close();
	}
	
	public static void run() {
		Assembly assembly = new Assembly("passar nos parametros", "passar nos parametros", new Date(), "Java");
		
		//itera nas classes
		for (Iterator<SootClass> klassIt = Scene.v().getApplicationClasses().iterator(); klassIt.hasNext();) {
			final SootClass klass = (SootClass) klassIt.next();
			
			Type type = new Type(assembly, klass.getName(), "ver como pegar isso");
			
			List<SootMethod> methods = klass.getMethods();
			//itera nos métodos
			for (Iterator<SootMethod> methodsIt = methods.iterator(); methodsIt.hasNext(); ) {
				SootMethod sootMethod = (SootMethod) methodsIt.next();
				
				Method method = new Method(type, sootMethod.getName(), getVisibility(sootMethod.getModifiers()));
				type.addMethod(method);
				sootMethod.retrieveActiveBody();
				
				ArrayList<Unit> listTry= new ArrayList<Unit>();
				ArrayList<Unit> listFinally= new ArrayList<Unit>();
				ArrayList<Unit> units = new ArrayList<Unit>();
				
				UnitGraph graph = new TrapUnitGraph(sootMethod.getActiveBody());
				for (Iterator<Unit> graphIt = graph.iterator(); graphIt.hasNext();) { //itera nos statements atrás de throws
					Unit unit = graphIt.next();
					units.add(unit);
					if (unit instanceof JThrowStmt) {
						JThrowStmt throwUnit = (JThrowStmt) unit;
						String throwType = throwUnit.getOp().getType().toString();
						
						if (!throwType.equals("java.lang.Throwable")){ //não é um throw genérico usado pelo compilador
							new Throw(method, throwType, units.indexOf(unit));
						}
					}
					
					//cria os MethodCall com targets ficticios
					if (unit instanceof Stmt) {
						Stmt stmt = (Stmt) unit;
						
						if (stmt.containsInvokeExpr()) {
							InvokeExpr invokeExpr;
							invokeExpr = stmt.getInvokeExpr();
							SootMethodRef methodRef = invokeExpr.getMethodRef();

							FakeMethod fakeTarget = new FakeMethod(methodRef.declaringClass().getName(), methodRef.name());

							MethodCall.createWithFakeTarget(assembly, method, fakeTarget, units.indexOf(unit));
						}
					}
				}

				for (Iterator<Trap> i = sootMethod.getActiveBody().getTraps().iterator(); i.hasNext();) {
					Trap trap = i.next();

					if (trap.getException().getName().equals("java.lang.Throwable")) { //é um finally
						if (!listFinally.contains(trap.getHandlerUnit())) {
							listFinally.add(trap.getHandlerUnit());
						}
						if (trap.getHandlerUnit() == trap.getEndUnit()) { //é um novo try, sem catchs
							listTry.add(trap.getEndUnit());
							new Try(method, units.indexOf(trap.getBeginUnit()), units.indexOf(trap.getEndUnit())); //obs: início e fim do try é errado
						}
					} else { //é um catch
						if (!listTry.contains(trap.getEndUnit())) { //é um novo try de um grupo de catchs
							listTry.add(trap.getEndUnit());
							new Try(method, units.indexOf(trap.getBeginUnit()), units.indexOf(trap.getEndUnit()));  //obs: início e fim do try é errado
						}
						new Catch(method, trap.getException().getName(), units.indexOf(trap.getBeginUnit()), units.indexOf(trap.getEndUnit()));  //obs: início e fim do try é errado
					}
				}
				
				method.setQtdFinally(listFinally.size());
	        }
		}

		MethodCall.trackActualTargets();
	}
	
	public static String getVisibility(int modifiers) {
		
		if ((modifiers | soot.Modifier.PUBLIC) == modifiers) {
			return "public";
		} else if ((modifiers | soot.Modifier.PRIVATE) == modifiers) {
			return "private";
		} else if ((modifiers | soot.Modifier.PROTECTED) == modifiers) {
			return "protected";
		} else {
			return "unknown";
		} 
	}

	public static String getXML() throws java.lang.Exception {
	    XStream xstream = new XStream();
	    xstream.autodetectAnnotations(true);
	    xstream.setMode(XStream.ID_REFERENCES);

	    xstream.alias("assembly", Assembly.class);
	    xstream.alias("type", Type.class);
	    xstream.alias("method", Method.class);
	    xstream.alias("methodCall", MethodCall.class);
	    xstream.alias("methodException", MethodException.class);
	    xstream.alias("methodException", Try.class);
	    xstream.alias("methodException", Throw.class);
	    xstream.alias("methodException", Catch.class);
	    xstream.alias("exception", Exception.class);
	    return xstream.toXML(Assembly.getInstance());
	}
}