package com.sun.s1asdev.ejb.ee.ejb;

import java.rmi.RemoteException;
import javax.ejb.EJBHome;
import javax.ejb.CreateException;

public interface SFSBHome
    extends EJBHome
{
    public SFSB create(String accountHolderName, int balance)
	throws CreateException, RemoteException;

}
