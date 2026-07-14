// Minimal C# console app to test breakpoint placement in Rider
// (repro for Cucumber+ issue #128 / JetBrains SUPPORT-A-4058).
// Click the left gutter on the marked line to place a breakpoint.

int sum = 0;
for (int i = 1; i <= 5; i++)
{
    sum += i;                                  // ← place a BREAKPOINT here (gutter click)
    Console.WriteLine($"i={i}  sum={sum}");
}
Console.WriteLine($"Total = {sum}");
