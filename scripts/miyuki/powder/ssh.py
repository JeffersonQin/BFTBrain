#!/usr/bin/env python3
import logging
import os
import pexpect
import re
import time


class SSHConnection:
    """A simple ssh/scp wrapper for creating and interacting with ssh sessions via
    pexpect.

    Args:
        ip_address (str): IP address of the node.
        username (str): A username with access to the node.
        prompt (str) (optional): Expected prompt on the host.

    Attributes:
        ssh (pexpect child): A handle to a session started by pexpect.spawn()
    """

    DEFAULT_PROMPT = '\$'

    def __init__(self, ip_address, username=None, password=None, prompt=DEFAULT_PROMPT):
        self.prompt = prompt
        self.ip_address = ip_address
        if username is None:
            try:
                self.username = os.environ['USER']
            except KeyError:
                logging.error('no USER variable in environment variable and no username provided')
                raise ValueError

        if password is None:
            try:
                self.password = os.environ['KEYPWORD']
            except KeyError:
                logging.info('no ssh key password in environment, assuming unencrypted')
                self.password = password

    def open(self):
        """Opens a connection to `self.ip_address` using `self.username`."""

        retry_count = 0
        cmd = 'ssh -l {} {}'.format(self.username, self.ip_address)
        while retry_count < 4:
            self.ssh = pexpect.spawn(cmd, timeout=5)
            self.sshresponse = self.ssh.expect([self.prompt,
                                                'Are you sure you want to continue connecting (yes/no)?',
                                                'Last login',
                                                'Enter passphrase for key.*:',
                                                pexpect.EOF,
                                                pexpect.TIMEOUT])
            if self.sshresponse == 0:
                return self
            elif self.sshresponse == 1:
                self.ssh.sendline('yes')
                self.sshresponse = self.ssh.expect([self.prompt,
                                                    'Enter passphrase for key.*:',
                                                    'Permission denied',
                                                    pexpect.EOF,
                                                    pexpect.TIMEOUT])
                if self.sshresponse == 0:
                    return self
                elif self.sshresponse == 1:
                    if self.password is None:
                        logging.error('failed to login --- ssh key is encrypted but no pword provided')
                        raise ValueError

                    self.ssh.sendline(self.password)
                    self.sshresponse = self.ssh.expect([self.prompt, 'Permission denied', pexpect.EOF, pexpect.TIMEOUT])
                    if self.sshresponse == 0:
                        return self
                    else:
                        logging.debug('failed to login --- response: {}'.format(self.sshresponse))
                        logging.debug('retry count: {}'.format(retry_count))
                else:
                    logging.debug('failed to login --- response: {}'.format(self.sshresponse))
                    logging.debug('retry count: {}'.format(retry_count))

            elif self.sshresponse == 2:
                # Verify we've connected to the self.ip_address
                self.command('ifconfig | egrep --color=never "inet addr:|inet "', self.prompt)
                self.sshresponse = self.ssh.expect([self.prompt, pexpect.EOF, pexpect.TIMEOUT])
                result = re.search(str(self.ip_address), str(self.ssh.before))
                if result is None:
                    logging.debug('not on host with ip {}'.format(self.ip_address))
                    logging.debug('retry count: {}'.format(retry_count))
                else:
                    return self

            elif self.sshresponse == 3:
                if self.password is None:
                    logging.error('failed to login --- ssh key is encrypted but no pword provided')
                    raise ValueError

                self.ssh.sendline(self.password)
                self.sshresponse = self.ssh.expect([self.prompt, 'Permission denied', pexpect.EOF, pexpect.TIMEOUT])

                if self.sshresponse == 0:
                    return self
                else:
                    logging.debug('failed to login --- response: {}'.format(self.sshresponse))
                    logging.debug('retry count: {}'.format(retry_count))

            elif self.sshresponse == 4:
                logging.debug('Unexpected EOF')
                logging.debug('retry count: {}'.format(retry_count))
                logging.debug('ssh.before: ' + str(self.ssh.before))
            elif self.sshresponse == 5:
                logging.debug('Unexpected Timeout')
                logging.debug('retry count: {}'.format(retry_count))
                logging.debug('ssh.before: ' + str(self.ssh.before))

            time.sleep(1)
            retry_count += 1

        logging.error('failed to login --- could not connect to host.')
        raise ValueError

    def command(self, commandline, expectedline=DEFAULT_PROMPT, timeout=5):
        """Sends `commandline` to `self.ip_address` and waits for `expectedline`."""
        logging.debug(commandline)
        self.ssh.sendline(commandline)
        self.sshresponse = self.ssh.expect([expectedline, pexpect.EOF, pexpect.TIMEOUT], timeout=timeout)
        if self.sshresponse == 0:
            pass
        elif self.sshresponse == 1:
            logging.debug('Unexpected EOF --- Expected: ' + expectedline)
            logging.debug('ssh.before: ' + str(self.ssh.before))
        elif self.sshresponse == 2:
            logging.debug('Unexpected Timeout --- Expected: ' + expectedline)
            logging.debug('ssh.before: ' + str(self.ssh.before))

        return self.sshresponse

    def close(self, timeout):
        self.ssh.sendline('exit')
        self.sshresponse = self.ssh.expect([pexpect.EOF, pexpect.TIMEOUT], timeout=timeout)
        if self.sshresponse == 0:
            pass
        elif self.sshresponse == 1:
            logging.debug('Unexpected Timeout --- Expected: EOF')
            logging.debug('ssh.before: ' + str(self.ssh.before))

        return self.sshresponse

    def copy_from(self, remote_path, local_path):
        cmd = 'scp {}@{}:{} {}'.format(self.username, self.ip_address, remote_path, local_path)
        return self.copy(cmd)

    def copy_to(self, local_path, remote_path):
        cmd = 'scp {} {}@{}:{}'.format(local_path, self.username, self.ip_address, remote_path)
        return self.copy(cmd)

    def copy(self, cmd):
        retry_count = 0
        logging.debug(cmd)
        while retry_count < 10:
            scp_spawn = pexpect.spawn(cmd, timeout = 100)
            scp_response = scp_spawn.expect(['Are you sure you want to continue connecting (yes/no)?',
                                             'Enter passphrase for key.*:',
                                             pexpect.EOF,
                                             pexpect.TIMEOUT])
            if scp_response == 0:
                scp_spawn.sendline('yes')
                scp_response = scp_spawn.expect([self.prompt,
                                                 'Enter passphrase for key.*:',
                                                 'Permission denied',
                                                 pexpect.EOF,
                                                 pexpect.TIMEOUT])
                if scp_response == 0:
                    return scp_response
                elif scp_response == 1:
                    if self.password is None:
                        logging.error('failed to scp --- ssh key is encrypted but no pword provided')
                        raise ValueError

                    scp_spawn.sendline(self.password)
                    scp_response = scp_spawn.expect([self.prompt, 'Permission denied', pexpect.EOF, pexpect.TIMEOUT])
                    if scp_response == 0:
                        return scp_response
                    else:
                        logging.debug('failed to scp --- response: {}'.format(scp_response))
                        logging.debug('retry count: {}'.format(retry_count))
                    return scp_response
                else:
                    logging.debug('scp failed with scp response {}'.format(scp_response))
                    logging.debug('retry count: {}'.format(retry_count))
            elif scp_response == 1:
                if self.password is None:
                    logging.error('failed to scp --- ssh key is encrypted but no pword provided')
                    raise ValueError

                scp_spawn.sendline(self.password)
                scp_response = scp_spawn.expect([self.prompt, 'Permission denied', pexpect.EOF, pexpect.TIMEOUT])
                if scp_response == 0:
                    return scp_response
                else:
                    logging.debug('failed to scp --- response: {}'.format(scp_response))
                    logging.debug('retry count: {}'.format(retry_count))
                return scp_response
            elif scp_response == 2:
                logging.debug('copy succeeded')
                return scp_response

            time.sleep(1)
            retry_count += 1

        return scp_response
