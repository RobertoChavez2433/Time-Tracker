using System.Runtime.InteropServices;

namespace WorkHubBridge;

public static class WindowsCredentialStore
{
    public static string? Read(string target)
    {
        if (!CredRead(target, 1, 0, out var pointer)) return null;
        try
        {
            var credential = Marshal.PtrToStructure<Credential>(pointer);
            return credential.CredentialBlobSize == 0 ? string.Empty : Marshal.PtrToStringUni(credential.CredentialBlob, (int)credential.CredentialBlobSize / 2);
        }
        finally { CredFree(pointer); }
    }

    public static void Write(string target, string secret)
    {
        var blob = Marshal.StringToCoTaskMemUni(secret);
        try
        {
            var credential = new Credential
            {
                Type = 1,
                TargetName = target,
                CredentialBlobSize = (uint)(secret.Length * 2),
                CredentialBlob = blob,
                Persist = 2,
                UserName = Environment.UserName,
                Comment = "Time Tracker Work Hub Notion integration"
            };
            if (!CredWrite(ref credential, 0)) throw new InvalidOperationException($"Credential Manager rejected the token ({Marshal.GetLastWin32Error()}).");
        }
        finally { Marshal.FreeCoTaskMem(blob); }
    }

    [DllImport("advapi32.dll", EntryPoint = "CredReadW", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CredRead(string target, int type, int flags, out IntPtr credential);
    [DllImport("advapi32.dll", EntryPoint = "CredWriteW", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CredWrite(ref Credential credential, uint flags);
    [DllImport("advapi32.dll")] private static extern void CredFree(IntPtr credential);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct Credential
    {
        public uint Flags;
        public uint Type;
        public string TargetName;
        public string? Comment;
        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        public uint CredentialBlobSize;
        public IntPtr CredentialBlob;
        public uint Persist;
        public uint AttributeCount;
        public IntPtr Attributes;
        public string? TargetAlias;
        public string UserName;
    }
}

public static class SecretConsole
{
    public static string Read()
    {
        var characters = new List<char>();
        ConsoleKeyInfo key;
        while ((key = Console.ReadKey(true)).Key != ConsoleKey.Enter)
        {
            if (key.Key == ConsoleKey.Backspace && characters.Count > 0) characters.RemoveAt(characters.Count - 1);
            else if (!char.IsControl(key.KeyChar)) characters.Add(key.KeyChar);
        }
        Console.WriteLine();
        return new string(characters.ToArray());
    }
}
