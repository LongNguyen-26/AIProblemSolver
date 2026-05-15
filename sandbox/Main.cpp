#include <bits/stdc++.h>
using namespace std;

int main(){
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int T; if(!(cin>>T)) return 0;
    while(T--){
        int n,k;cin>>n>>k;
        int z=n-k;
        if(k==0){
            cout<<string(n,'0')<<"\n";
            continue;
        }
        if(k==n){
            cout<<string(n,'1')<<"\n";
            continue;
        }
        long long bestVal=LLONG_MAX; int bestR=1;
        for(int r=1;r<=k;r++){
            int zeroRuns=r+1;
            int L0 = (z + zeroRuns -1)/zeroRuns; // ceil
            int S1 = k / r; // floor
            long long val = 1LL*L0*(n+1) - S1;
            if(val<bestVal){bestVal=val;bestR=r;}
        }
        int r=bestR; // number of one-runs
        int zeroRuns=r+1;
        int baseZero = z/zeroRuns; int extraZero = z%zeroRuns;
        int baseOne = k/r; int extraOne = k%r;
        string ans; ans.reserve(n);
        for(int i=0;i<zeroRuns;i++){
            int lenZero = baseZero + (i<extraZero);
            ans.append(lenZero,'0');
            if(i<r){
                int lenOne = baseOne + (i<extraOne);
                ans.append(lenOne,'1');
            }
        }
        // ans length should be n
        cout<<ans<<"\n";
    }
    return 0;
}
